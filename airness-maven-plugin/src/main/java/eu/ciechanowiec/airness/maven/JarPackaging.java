package eu.ciechanowiec.airness.maven;

import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * The packagings whose build produces a JAR to inspect.
 *
 * <p>Held once rather than beside each goal that asks. Two copies of this set decide whether a check
 * runs at all, so a packaging added to one and not the other would leave one of them silently passing
 * over an artifact the other reads.
 */
@UtilityClass
final class JarPackaging {

    private static final Set<String> NAMES = Set.of("jar", "maven-plugin", "bundle");

    /**
     * Whether a project of this packaging produces a JAR.
     *
     * @param packaging the project packaging as Maven resolved it
     * @return whether the build leaves a JAR behind
     */
    static boolean produced(String packaging) {
        return NAMES.contains(packaging);
    }
}
