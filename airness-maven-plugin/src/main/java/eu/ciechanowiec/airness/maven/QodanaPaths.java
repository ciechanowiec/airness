package eu.ciechanowiec.airness.maven;

import java.nio.file.Path;

/**
 * Host paths mounted into the Qodana container.
 *
 * @param root        repository root
 * @param output      Qodana output directory
 * @param profile     extracted inspection profile
 * @param environment host trust roots and resolved Maven repository
 */
record QodanaPaths(
    Path root, Path output, Path profile, Environment environment
) {

    Path roots() {
        return this.environment.roots();
    }

    Path localRepository() {
        return this.environment.localRepository();
    }

    /**
     * Host paths Qodana reads as its Maven and certificate environment.
     *
     * @param roots           exported host trust roots
     * @param localRepository resolved Maven repository
     */
    public record Environment(Path roots, Path localRepository) {
    }
}
