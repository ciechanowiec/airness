package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.DeclaredCoordinate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;

/**
 * The coordinates of a module's effective build: every plugin it binds or manages, wherever it was declared,
 * and every dependency such a plugin is given.
 *
 * <p>The raw pom answers only for what it declares with a version. A plugin written without one, or declared
 * by a parent outside the repository, is still in the effective build, with the version Maven chose for it.
 */
@UtilityClass
final class PluginCoordinates {

    /**
     * Reads the effective build.
     *
     * @param build   the module's build plugins
     * @param managed the plugins the module's plugin management names
     * @return the coordinates, a version left empty where the model carries none
     */
    static List<DeclaredCoordinate> of(Collection<Plugin> build, Collection<Plugin> managed) {
        return Stream.concat(build.stream(), managed.stream())
            .flatMap(PluginCoordinates::coordinates)
            .distinct()
            .toList();
    }

    private static Stream<DeclaredCoordinate> coordinates(Plugin plugin) {
        DeclaredCoordinate own = coordinate(plugin.getGroupId(), plugin.getArtifactId(), plugin.getVersion());
        return Stream.concat(Stream.of(own), plugin.getDependencies().stream().map(PluginCoordinates::coordinate));
    }

    private static DeclaredCoordinate coordinate(Dependency dependency) {
        return coordinate(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion());
    }

    private static DeclaredCoordinate coordinate(String group, String artifact, String version) {
        return new DeclaredCoordinate(group, artifact, Optional.ofNullable(version).orElse(""));
    }
}
