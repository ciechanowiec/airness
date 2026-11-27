package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Keeps a module's node projects on pnpm's one lockfile, and on registries reached over https with a checked
 * certificate.
 *
 * <p>pnpm reads only {@code pnpm-lock.yaml}. A lockfile of npm, Yarn or Bun beside it is never checked, yet a
 * dependency scanner, an update bot or a Dockerfile that runs {@code npm ci} reads it, and reports or installs
 * versions no gate judged. A project's {@code .npmrc} or {@code pnpm-workspace.yaml} that turns the certificate
 * check off, or names a registry over plain http, lets anybody on the network path hand pnpm another package the
 * first time it resolves one. A registry on the loopback address never leaves the machine, as the one that serves
 * a harness SNAPSHOT does, so plain http is admitted there.
 */
final class NodeInstallFiles {

    static final String NPMRC = ".npmrc";

    private static final Set<String> FOREIGN_LOCKFILES = Set.of(
        "package-lock.json", "npm-shrinkwrap.json", "yarn.lock", "bun.lock", "bun.lockb"
    );
    private static final Set<String> CERTIFICATE_CHECKS = Set.of("strict-ssl", "strictSsl");
    private static final Pattern PLAIN = Pattern.compile("(?i)http://.*");
    private static final Pattern LOOPBACK = Pattern.compile(
        "(?i)http://(localhost|127(\\.\\d{1,3}){3}|\\[::1])(:\\d+)?(/.*)?"
    );
    private static final Pattern QUOTED = Pattern.compile("([\"'])(.*)\\1");

    private final Path module;
    private final Collection<Path> files;

    /**
     * Records the files of one module.
     *
     * @param module the module directory
     * @param files  the module's files
     */
    NodeInstallFiles(Path module, Collection<Path> files) {
        this.module = module;
        this.files = List.copyOf(files);
    }

    /**
     * The verdicts over the module's lockfiles and registry settings.
     *
     * @return one verdict per rule
     */
    List<Findings> findings() {
        return List.of(
            new Findings("Keep pnpm-lock.yaml the only lockfile of a node project", this.lockfiles()),
            new Findings(
                "Reach every npm registry over https with its certificate checked", this.transport()
            )
        );
    }

    private List<String> lockfiles() {
        return this.files.stream()
            .filter(file -> FOREIGN_LOCKFILES.contains(String.valueOf(file.getFileName())))
            .map(file -> this.named(file) + " is a lockfile pnpm never reads; remove it")
            .toList();
    }

    private List<String> transport() {
        return this.files.stream().flatMap(this::insecureSettings).toList();
    }

    private Stream<String> insecureSettings(Path file) {
        String name = this.named(file);
        return settings(file).flatMap(setting -> insecure(setting).stream()).map(problem -> name + ": " + problem);
    }

    // The settings a file gives pnpm, as key and value: an .npmrc line by line, a workspace file by its mapping.
    private static Stream<Map.Entry<String, String>> settings(Path file) {
        String name = String.valueOf(file.getFileName());
        if (NPMRC.equals(name)) {
            return npmrc(file);
        }
        return PnpmWorkspace.FILE.equals(name)
            ? PnpmWorkspace.settings(file).entrySet().stream()
                .map(entry -> Map.entry(String.valueOf(entry.getKey()), String.valueOf(entry.getValue())))
            : Stream.empty();
    }

    private static Optional<String> insecure(Map.Entry<String, String> setting) {
        String key = setting.getKey();
        String value = setting.getValue();
        if (CERTIFICATE_CHECKS.contains(key)) {
            return Optional.of(key + " " + value + " turns the registry's certificate check off")
                .filter(_ -> "false".equalsIgnoreCase(value));
        }
        boolean registry = NpmRegistries.REGISTRY.equals(key) || key.endsWith(":" + NpmRegistries.REGISTRY);
        return Optional.of(key + " " + value + " is reached over plain http")
            .filter(_ -> registry && PLAIN.matcher(value).matches() && !LOOPBACK.matcher(value).matches());
    }

    // An .npmrc is an ini file: a line starting with a hash or a semicolon is a comment, and a value may be quoted.
    private static Stream<Map.Entry<String, String>> npmrc(Path file) {
        try {
            return Files.readAllLines(file).stream()
                .map(String::strip)
                .filter(line -> !line.startsWith("#") && !line.startsWith(";") && line.contains("="))
                .map(
                    line -> Map.entry(
                        line.substring(0, line.indexOf('=')).strip(),
                        unquoted(line.substring(line.indexOf('=') + 1).strip())
                    )
                );
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + file, exception);
        }
    }

    private static String unquoted(String value) {
        Matcher quoted = QUOTED.matcher(value);
        return quoted.matches() ? quoted.group(2) : value;
    }

    private String named(Path file) {
        return this.module.relativize(file).toString().replace('\\', '/');
    }
}
