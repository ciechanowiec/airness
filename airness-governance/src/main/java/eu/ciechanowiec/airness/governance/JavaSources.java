package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Selects the committable Java sources that lie under a declared set of source roots.
 *
 * <p>A root arrives either relative to the repository, as a project states it, or absolute, as Maven
 * hands one over in {@code project.compileSourceRoots}. Resolving against the repository root handles
 * both, since resolving an absolute path returns it unchanged, so a caller never has to say which kind
 * it holds.
 *
 * <p>An empty result is a silent failure rather than a clean one: a check that read no file reports the
 * same verdict as a check that read a clean tree. Refusing that is the caller's job, because only the
 * caller knows whether the module it is looking at is meant to have sources at all.
 */
@UtilityClass
final class JavaSources {

    private static final String JAVA = ".java";

    /**
     * Every tracked Java source beneath any of the given roots.
     *
     * @param root        the working tree root
     * @param sourceRoots the directories to read, each relative to {@code root} or absolute
     * @return the matching sources
     */
    static List<Path> under(Path root, Collection<Path> sourceRoots) {
        List<Path> resolved = sourceRoots.stream().map(root::resolve).map(Path::normalize).toList();
        return Repository.trackedFiles(root).stream()
            .filter(file -> file.getFileName().toString().endsWith(JAVA))
            .filter(file -> resolved.stream().anyMatch(file::startsWith))
            .toList();
    }
}
