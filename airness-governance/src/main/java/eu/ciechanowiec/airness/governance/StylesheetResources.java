package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.StreamSupport;
import lombok.experimental.UtilityClass;

/**
 * Every stylesheet a module ships.
 */
@UtilityClass
final class StylesheetResources {

    private static final String SUFFIX = ".css";

    // A stylesheet a project did not write and may not edit. The formatter of this harness already
    // passes over this directory for the same reason, so a rule about how a selector is written would
    // otherwise report a published file against a style its publisher never agreed to.
    private static final String VENDORED = "vendor";

    /**
     * Answers every stylesheet under the given resource directories that the project itself wrote.
     *
     * @param root          repository root the files are resolved against
     * @param resourceRoots resource directories of the module
     * @return the stylesheets, in the order git reports them
     */
    static List<Path> of(Path root, Collection<Path> resourceRoots) {
        return ModuleResources.of(root, resourceRoots, SUFFIX).stream().filter(StylesheetResources::written).toList();
    }

    // A file is the project's own unless a directory on its path is the one vendored parts are kept in.
    // Whole segments are compared, so a directory merely beginning with the same letters is still read.
    private static boolean written(Path file) {
        return StreamSupport.stream(file.spliterator(), false).map(Path::toString).noneMatch(VENDORED::equals);
    }
}
