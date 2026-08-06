package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.DeclaredCoordinate;
import eu.ciechanowiec.airness.governance.DeclaredCoordinates;
import eu.ciechanowiec.airness.governance.OwnedCoordinate;
import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

/**
 * Finds every raw pom that owns versions affecting the current build: the reactor and every direct
 * or indirect parent. Coordinates produced by the same reactor are excluded because no registry
 * release can replace an artifact being built in this session.
 */
@UtilityClass
final class VersionCoordinates {

    private static final String SEPARATOR = ":";
    private static final String POM_SUFFIX = ".pom";

    static List<OwnedCoordinate> from(MavenSession session) {
        Path repository = session.getRepositorySession().getLocalRepositoryManager()
            .getRepository().getBasedir().toPath();
        return from(session.getAllProjects(), repository);
    }

    static List<OwnedCoordinate> from(Collection<MavenProject> projects, Path repository) {
        Map<String, VersionPom> reactor = projects.stream()
            .map(VersionCoordinates::versionPom)
            .collect(Collectors.toUnmodifiableMap(VersionPom::key, Function.identity()));
        Set<String> reactorCoordinates = Set.copyOf(reactor.keySet());
        return reactor.values().stream()
            .flatMap(pom -> lineage(pom, reactor, repository))
            .distinct()
            .flatMap(VersionCoordinates::declared)
            .filter(declared -> !reactorCoordinates.contains(versionedKey(declared.coordinate())))
            .distinct()
            .toList();
    }

    private static Stream<VersionPom> lineage(
        VersionPom pom, Map<String, VersionPom> reactor, Path repository
    ) {
        Stream<VersionPom> parent = DeclaredCoordinates.parent(pom.file())
            .map(coordinate -> parentPom(coordinate, reactor, repository))
            .stream()
            .flatMap(found -> lineage(found, reactor, repository));
        return Stream.concat(Stream.of(pom), parent);
    }

    private static VersionPom parentPom(
        DeclaredCoordinate coordinate, Map<String, VersionPom> reactor, Path repository
    ) {
        return Optional.ofNullable(reactor.get(versionedKey(coordinate)))
            .orElseGet(
                () -> new VersionPom(
                    owner(coordinate), versionedKey(coordinate), localPom(coordinate, repository)
                )
            );
    }

    private static Stream<OwnedCoordinate> declared(VersionPom pom) {
        return DeclaredCoordinates.from(pom.file()).stream()
            .map(coordinate -> new OwnedCoordinate(pom.owner(), coordinate));
    }

    private static VersionPom versionPom(MavenProject project) {
        String owner = project.getGroupId() + SEPARATOR + project.getArtifactId();
        Path file = Optional.ofNullable(project.getFile()).map(File::toPath).orElseThrow();
        return new VersionPom(owner, versionedKey(project), file);
    }

    private static Path localPom(DeclaredCoordinate coordinate, Path repository) {
        String group = coordinate.groupId().replace('.', File.separatorChar);
        String filename = coordinate.artifactId() + '-' + coordinate.version() + POM_SUFFIX;
        return repository.resolve(group)
            .resolve(coordinate.artifactId())
            .resolve(coordinate.version())
            .resolve(filename);
    }

    private static String owner(DeclaredCoordinate coordinate) {
        return coordinate.groupId() + SEPARATOR + coordinate.artifactId();
    }

    private static String versionedKey(DeclaredCoordinate coordinate) {
        return owner(coordinate) + SEPARATOR + coordinate.version();
    }

    private static String versionedKey(MavenProject project) {
        return project.getGroupId() + SEPARATOR + project.getArtifactId() + SEPARATOR
            + project.getVersion();
    }

    private record VersionPom(String owner, String key, Path file) {
    }
}
