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
        return active(project, EXTENDED);
    }

    /**
     * Whether a profile is active for a project, declared in its own model or injected from a parent's, which
     * Maven 3 lists apart.
     *
     * @param project the project
     * @param id      the profile id
     * @return whether the profile is active
     */
    static boolean active(MavenProject project, String id) {
        return Stream.concat(
            project.getActiveProfiles().stream().map(Profile::getId),
            project.getInjectedProfileIds().values().stream().flatMap(Collection::stream)
        ).anyMatch(id::equals);
    }

    static MavenProject root(List<MavenProject> projects) {
        return projects.stream().filter(MavenProject::isExecutionRoot).findFirst().orElseGet(projects::getFirst);
    }

    static List<MavenProject> required(List<MavenProject> projects) {
        return projects.stream().filter(project -> !selfRoot(project)).toList();
    }

    static List<String> qualifications(Collection<MavenProject> selected, boolean partial, String enforcement) {
        return Stream.<Stream<String>>of(
            partial ? Stream.of("This invocation selected only part of the reactor.") : Stream.empty(),
            "false".equals(enforcement) ? Stream.of("Report-only mode does not establish an Airness verification pass.")
                : Stream.empty(),
            selected.stream().anyMatch(project -> active(project, "delta"))
                ? Stream.of(
                    "Delta verification scopes the browser sweep to the change. "
                        + "This is not a complete Extended verdict."
                )
                : Stream.empty()
        ).flatMap(stream -> stream).toList();
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
