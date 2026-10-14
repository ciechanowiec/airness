package eu.ciechanowiec.airness.maven;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.maven.model.Profile;
import org.apache.maven.project.MavenProject;

/**
 * Selects report participants without making a root project's early completion the reactor's completion.
 */
@UtilityClass
final class ReportProjects {

    private static final String EXTENDED = "extended";

    static boolean extended(MavenProject project) {
        return Stream.concat(
            project.getActiveProfiles().stream().map(Profile::getId),
            project.getInjectedProfileIds().values().stream().flatMap(Collection::stream)
        ).anyMatch(EXTENDED::equals);
    }

    static MavenProject root(List<MavenProject> projects) {
        return projects.stream().filter(MavenProject::isExecutionRoot).findFirst().orElseGet(projects::getFirst);
    }

    static List<MavenProject> required(List<MavenProject> projects) {
        return projects.stream().filter(project -> !selfRoot(project)).toList();
    }

    static List<MavenProject> ready(Collection<MavenProject> completed, MavenProject current, boolean self) {
        return self ? required(Stream.concat(completed.stream(), Stream.of(current)).distinct().toList())
            : List.of(current);
    }

    static List<String> qualifications(boolean partial, String enforcement) {
        return Stream.concat(
            partial ? Stream.of("This invocation selected only part of the reactor.") : Stream.empty(),
            "false".equals(enforcement) ? Stream.of("Report-only mode does not establish an Airness verification pass.")
                : Stream.empty()
        ).toList();
    }

    static boolean partial(Collection<?> selected, Collection<?> all) {
        return selected.size() != all.size();
    }

    static MavenProject executed(MavenProject project) {
        return Optional.ofNullable(project.getExecutionProject()).orElse(project);
    }

    private static boolean selfRoot(MavenProject project) {
        return "eu.ciechanowiec".equals(project.getGroupId()) && "airness".equals(project.getArtifactId());
    }
}
