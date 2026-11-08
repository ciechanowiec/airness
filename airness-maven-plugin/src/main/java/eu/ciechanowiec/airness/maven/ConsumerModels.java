package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ManagedVersions;
import eu.ciechanowiec.airness.governance.MavenModelPolicy;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.SessionData;

/**
 * Raw consumer models up to the trusted Airness parent, including parents outside the reactor.
 */
@UtilityClass
final class ConsumerModels {

    static List<String> problems(MavenProject project, SessionData data, LocalRepository repository) {
        return Stream.iterate(project, Objects::nonNull, MavenProject::getParent)
            .takeWhile(parent -> !RepositoryProjects.harnessParent(parent))
            .map(parent -> Map.entry(pom(parent, repository), parent))
            .filter(model -> OncePerSession.firstRun(data, ConsumerModels.class, model.getKey().toString()))
            .flatMap(model -> pomProblems(model.getKey(), inherited(model.getValue())))
            .toList();
    }

    /**
     * The ids of the Airness plugin executions a project inherits, bound or only managed, which a declaration
     * of its own must not reuse.
     *
     * @param project the project
     * @return the execution ids its parent's effective model carries
     */
    static Set<String> inherited(MavenProject project) {
        return Optional.ofNullable(project.getParent()).stream()
            .flatMap(ConsumerModels::plugins)
            .filter(AirnessCoordinates::isPlugin)
            .flatMap(plugin -> plugin.getExecutions().stream())
            .map(PluginExecution::getId)
            .collect(Collectors.toUnmodifiableSet());
    }

    private static Path pom(MavenProject project, LocalRepository repository) {
        return VersionCoordinates.rawPom(project, repository).toAbsolutePath().normalize();
    }

    private static Stream<Plugin> plugins(MavenProject project) {
        Stream<Plugin> managed = Optional.ofNullable(project.getPluginManagement()).stream()
            .flatMap(management -> management.getPlugins().stream());
        return Stream.concat(project.getBuildPlugins().stream(), managed);
    }

    private static Stream<String> pomProblems(Path pom, Set<String> inherited) {
        return Stream.concat(ManagedVersions.problems(pom, inherited).stream(), MavenModelPolicy.problems(pom).stream())
            .map(problem -> pom + ": " + problem);
    }
}
