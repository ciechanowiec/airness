package eu.ciechanowiec.airness.governance;

import java.util.Optional;
import java.util.OptionalInt;
import lombok.experimental.UtilityClass;

/**
 * The dependency-freshness policy as a pure function: a declared coordinate fails when its comparable
 * version trails the latest stable version by the declared bound or more. A conventional version uses
 * its numeric major. A version beginning with a year in the range {@code 20**} uses that year as its
 * major-equivalent, so calendar-versioned dependencies obey the same two-level bound.
 */
@UtilityClass
final class DependencyFreshnessRules {

    private static final int MAJOR_FAIL_THRESHOLD = 2;
    private static final int YEAR_LENGTH = 4;
    private static final String YEAR_PREFIX = "20";
    private static final char SEPARATOR = '.';

    static Optional<String> violation(VersionUpdate update) {
        DeclaredCoordinate declared = update.declared().coordinate();
        OptionalInt latest = major(update.latest());
        return major(declared.version())
            .stream()
            .boxed()
            .filter(_ -> latest.isPresent())
            .filter(declaredMajor -> latest.orElseThrow() - declaredMajor >= MAJOR_FAIL_THRESHOLD)
            .map(declaredMajor -> render(update, declaredMajor, latest.orElseThrow()))
            .findFirst();
    }

    static boolean hasComparableMajor(String version) {
        return major(version).isPresent();
    }

    static OptionalInt major(String version) {
        if (isYearVersion(version)) {
            return OptionalInt.of(Integer.parseInt(version.substring(0, YEAR_LENGTH)));
        }
        int separator = version.indexOf(SEPARATOR);
        String head = separator > 0 ? version.substring(0, separator) : version;
        boolean numeric = !head.isEmpty() && head.chars().allMatch(Character::isDigit);
        return numeric ? OptionalInt.of(Integer.parseInt(head)) : OptionalInt.empty();
    }

    static boolean sameScheme(String declared, String candidate) {
        return isYearVersion(declared) == isYearVersion(candidate) && major(candidate).isPresent();
    }

    private static boolean isYearVersion(String version) {
        return version.length() >= YEAR_LENGTH
            && version.startsWith(YEAR_PREFIX)
            && version.substring(0, YEAR_LENGTH).chars().allMatch(Character::isDigit);
    }

    private static String render(VersionUpdate update, int declaredMajor, int latestMajor) {
        DeclaredCoordinate declared = update.declared().coordinate();
        return "[%s] %s:%s is at major %d but the latest stable major is %d".formatted(
            update.declared().owner(), declared.groupId(), declared.artifactId(), declaredMajor, latestMajor
        );
    }
}
