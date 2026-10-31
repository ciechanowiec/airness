package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.maven.NodeProjects.NodeProject;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * The checked source roots of a module's declared node projects, as directories.
 *
 * <p>A Java-side check over a kind of file the node gates also select reads these beside the module's
 * resource directories, so moving a file between the Java and the node portions of a module does not take
 * a rule away from it.
 */
@UtilityClass
final class NodeSourceRoots {

    /**
     * The checked source roots of every declared node project inside the module that has a manifest.
     *
     * @param declared the node projects the module declares
     * @return absolute, normalized directories, in declaration order and each once
     */
    static List<Path> of(NodeProjects declared) {
        return declared.projects().stream()
            .filter(project -> !project.outside() && project.hasManifest())
            .map(NodeProject::directory)
            .flatMap(NodeSourceRoots::rootsOf)
            .distinct()
            .toList();
    }

    private static Stream<Path> rootsOf(Path directory) {
        return NodeMember.at(directory).checkedRoots().stream()
            .map(root -> directory.resolve(root).toAbsolutePath().normalize());
    }
}
