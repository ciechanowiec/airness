package eu.ciechanowiec.airness.maven;

import java.nio.file.Path;
import java.util.List;

/**
 * Host paths mounted into the Qodana container.
 *
 * @param root        repository root
 * @param output      Qodana output directory
 * @param profile     extracted inspection profile
 * @param environment host trust roots and the resolved local Maven repository
 * @param metadata    the mounts of a linked worktree's Git directories, none for a clone
 */
record QodanaPaths(
    Path root, Path output, Path profile, Environment environment, List<String> metadata
) {

    QodanaPaths {
        metadata = List.copyOf(metadata);
    }

    Path roots() {
        return this.environment.roots();
    }

    LocalRepository localRepository() {
        return this.environment.localRepository();
    }

    /**
     * Host paths Qodana reads as its Maven and certificate environment.
     *
     * @param roots           exported host trust roots
     * @param localRepository the resolved local Maven repository, with every tail chained behind its head
     */
    public record Environment(Path roots, LocalRepository localRepository) {
    }
}
