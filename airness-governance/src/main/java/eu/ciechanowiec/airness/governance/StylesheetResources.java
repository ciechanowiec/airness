package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Every stylesheet a module ships.
 *
 * <p>A directory named {@code vendor} is read like any other. Its name is no evidence that somebody else
 * wrote what it holds, and a stylesheet a project ships is one it is held to. A published distribution is
 * taken from a dependency instead of being kept among the module's resources.
 */
@UtilityClass
final class StylesheetResources {

    private static final String SUFFIX = ".css";

    /**
     * Answers every stylesheet under the given directories.
     *
     * @param root          repository root the files are resolved against
     * @param resourceRoots resource directories of the module, and the checked source roots of its node projects
     * @return the stylesheets, in the order git reports them
     */
    static List<Path> of(Path root, Collection<Path> resourceRoots) {
        return ModuleResources.of(root, resourceRoots, SUFFIX);
    }
}
