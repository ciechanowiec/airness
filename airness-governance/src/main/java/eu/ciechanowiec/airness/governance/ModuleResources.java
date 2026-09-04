package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * The resources of one kind that a module ships, which is what every check over a shipped file reads.
 *
 * <p>The set is found through the resource directories the module declares rather than through a
 * directory named the way such files are usually kept. A name says where a project like this one
 * often keeps them and never where this project does, so a project that keeps them elsewhere would
 * otherwise pass by holding nothing the check looked for.
 *
 * <p>It is stated once here rather than in each check, because two checks over the same files that
 * disagreed about which files those are would report on different trees while reading as though they
 * read one. What differs between them is the kind of file they read, which is the one thing a caller
 * states.
 */
@UtilityClass
final class ModuleResources {

    /**
     * Every resource of the given kind that a commit from this tree would carry, under the given
     * module.
     *
     * @param root          the working tree root
     * @param resourceRoots resource directories of the module
     * @param suffix        what the name of a resource of this kind ends with
     * @return the files, in the order git reports them
     */
    static List<Path> of(Path root, Collection<Path> resourceRoots, String suffix) {
        List<Path> resolved = resourceRoots.stream().map(root::resolve).map(Path::normalize).toList();
        return Repository.trackedFiles(root).stream()
            .filter(file -> file.getFileName().toString().endsWith(suffix))
            .filter(file -> resolved.stream().anyMatch(file::startsWith))
            .toList();
    }
}
