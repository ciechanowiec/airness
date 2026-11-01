package eu.ciechanowiec.airness.maven;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Where a module lies and which of its directories the frontend coverage walk does not enter.
 *
 * @param root    the repository root the generated globs are relative to
 * @param basedir the module directory
 * @param skipped the directories the walk does not enter: child modules and the build directory
 */
record FrontendWalk(Path root, Path basedir, Collection<Path> skipped) {

    /**
     * Copies the skipped directories, so the walk cannot change after it was described.
     *
     * @param root    the repository root
     * @param basedir the module directory
     * @param skipped the directories the walk does not enter
     */
    FrontendWalk {
        skipped = List.copyOf(skipped);
    }
}
