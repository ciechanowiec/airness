package eu.ciechanowiec.airness.governance;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Whether one shell command upgrades the system packages a base image already holds.
 *
 * <p>A shipped image takes its system packages from its base, and a newer release of that base is the repair
 * for a package it ships vulnerable. An upgrade run inside the image patches the copy one build happened to
 * get instead, so what the image holds depends on the day it was built rather than on the release its
 * Dockerfile names, and the compliance check can no longer tell which release would have answered the advisory.
 *
 * <p>Two shapes upgrade: a manager's upgrade verb, as in {@code apk upgrade}, {@code apt-get dist-upgrade} or
 * {@code dnf update}, and an install that asks for the newer version of what is already there, as in
 * {@code apk add --upgrade} or {@code apt-get install --only-upgrade}. A refresh of the package index, such as
 * {@code apt-get update} or {@code apk update}, installs nothing and passes. The verb is matched as a whole
 * word anywhere after the manager, so options before it, with or without values, change nothing.
 */
@UtilityClass
final class PackageUpgrades {

    private static final Set<String> REDHAT = Set.of(
        "upgrade", "update", "distro-sync", "upgrade-minimal", "update-minimal"
    );
    private static final Set<String> DEBIAN = Set.of("upgrade", "dist-upgrade", "full-upgrade");
    private static final Map<String, Set<String>> VERBS = Map.of(
        "apk", Set.of("upgrade"),
        "apt-get", DEBIAN,
        "apt", DEBIAN,
        "aptitude", Set.of("upgrade", "safe-upgrade", "full-upgrade", "dist-upgrade"),
        "dnf", REDHAT,
        "yum", REDHAT,
        "microdnf", REDHAT,
        "tdnf", REDHAT,
        "zypper", Set.of("update", "up", "dist-upgrade", "dup", "patch")
    );
    private static final Set<String> INSTALLS = Set.of("add", "install");
    private static final Pattern BLANK = Pattern.compile("[ \\t]+");
    // apk's short options cluster, as in -Uu. Its lower-case u asks for the upgrade, and -U only refreshes the index.
    private static final Pattern APK_SHORT_UPGRADE = Pattern.compile("^-[A-Za-z]*u[A-Za-z]*$");
    private static final Set<String> UPGRADING_OPTIONS = Set.of("--upgrade", "--only-upgrade");
    private static final String APK = "apk";
    private static final char PATH_SEPARATOR = '/';

    /**
     * Whether the command upgrades packages the image already holds.
     *
     * @param command one command, separated from its neighbours on the {@code RUN} line
     * @return whether it runs an upgrade verb or an upgrading install
     */
    static boolean upgrades(String command) {
        List<String> tokens = Arrays.stream(BLANK.split(command.strip())).map(Quotes::stripped)
            .filter(token -> !token.isEmpty()).toList();
        for (int index = 0; index < tokens.size(); index++) {
            String manager = executable(tokens.get(index));
            Set<String> verbs = VERBS.getOrDefault(manager, Set.of());
            if (!verbs.isEmpty()) {
                return upgrades(manager, verbs, tokens.subList(index + 1, tokens.size()));
            }
        }
        return false;
    }

    private static boolean upgrades(String manager, Set<String> verbs, List<String> rest) {
        if (rest.stream().anyMatch(verbs::contains)) {
            return true;
        }
        boolean apk = APK.equals(manager);
        boolean installs = rest.stream().anyMatch(INSTALLS::contains);
        return installs && rest.stream().anyMatch(option -> upgradingOption(option, apk));
    }

    // An option that asks an install for the newer version of what is there: a long option any manager spells the
    // same, and apk's short cluster.
    private static boolean upgradingOption(String option, boolean apk) {
        boolean spelledOut = UPGRADING_OPTIONS.contains(option);
        boolean clustered = apk && APK_SHORT_UPGRADE.matcher(option).matches();
        return spelledOut || clustered;
    }

    // A manager named by its path, as in /sbin/apk, is the same manager.
    private static String executable(String token) {
        return token.substring(token.lastIndexOf(PATH_SEPARATOR) + 1);
    }
}
