package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * The message bundles a module ships, which is what every check over translated text reads.
 *
 * <p>The set is found through the resource directories the module declares rather than through a name
 * a bundle is usually given. A project keeps its bundles where it keeps its resources, and one that
 * named them something else would otherwise pass by holding nothing the check looked for.
 *
 * <p>It is stated once here rather than in each check, for the reason the markup is: two checks over
 * the same files that disagreed about which files those are would report on different trees while
 * reading as though they read one.
 */
@UtilityClass
final class MessageResources {

    private static final String SUFFIX = ".properties";

    /**
     * Every message bundle a commit from this tree would carry, under the given module.
     *
     * @param root          the working tree root
     * @param resourceRoots resource directories of the module
     * @return the bundle files, in the order git reports them
     */
    static List<Path> of(Path root, Collection<Path> resourceRoots) {
        return ModuleResources.of(root, resourceRoots, SUFFIX);
    }
}
