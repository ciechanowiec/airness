package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ManagedVersions;
import eu.ciechanowiec.airness.governance.MavenModelPolicy;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.SessionData;

/**
 * Raw consumer models up to the trusted Airness parent, including parents outside the reactor.
 */
@UtilityClass
final class ConsumerModels {

    static List<String> problems(MavenProject project, SessionData data, Path repository) {
        return Stream.iterate(project, Objects::nonNull, MavenProject::getParent)
            .takeWhile(parent -> !RepositoryProjects.harnessParent(parent))
            .map(parent -> VersionCoordinates.rawPom(parent, repository).toAbsolutePath().normalize())
            .filter(pom -> OncePerSession.firstRun(data, ConsumerModels.class, pom.toString()))
            .flatMap(ConsumerModels::problems)
            .toList();
    }

    private static Stream<String> problems(Path pom) {
        return Stream.concat(ManagedVersions.problems(pom).stream(), MavenModelPolicy.problems(pom).stream())
            .map(problem -> pom + ": " + problem);
    }
}
