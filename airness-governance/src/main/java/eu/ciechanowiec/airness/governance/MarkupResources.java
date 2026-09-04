package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * The markup a module ships, which is what every check over templates reads.
 *
 * <p>The set is found through the resource directories the module declares rather than through a
 * directory named like a template root. A name says where templates are usually kept and never where
 * this project keeps them, so a project that moves them would otherwise pass by holding nothing the
 * check looked for.
 *
 * <p>It is stated once here rather than in each check, because two checks over the same files that
 * disagreed about which files those are would report on different trees while reading as though they
 * read one.
 */
@UtilityClass
final class MarkupResources {

    private static final String SUFFIX = ".html";

    /**
     * Every markup resource a commit from this tree would carry, under the given module.
     *
     * @param root          the working tree root
     * @param resourceRoots resource directories of the module
     * @return the markup files, in the order git reports them
     */
    static List<Path> of(Path root, Collection<Path> resourceRoots) {
        return ModuleResources.of(root, resourceRoots, SUFFIX);
    }
}
