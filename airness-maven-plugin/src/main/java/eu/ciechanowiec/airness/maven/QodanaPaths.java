package eu.ciechanowiec.airness.maven;

import java.nio.file.Path;

/**
 * Host paths mounted into the Qodana container.
 *
 * @param root            repository root
 * @param output          Qodana output directory
 * @param profile         extracted inspection profile
 * @param roots           exported host trust roots
 * @param localRepository resolved Maven repository
 */
record QodanaPaths(
    Path root, Path output, Path profile, Path roots, Path localRepository
) {
}
