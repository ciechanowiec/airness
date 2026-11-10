package eu.ciechanowiec.airness.maven;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.model.Profile;
import org.apache.maven.project.MavenProject;

/**
 * Selects report participants without making a root project's early completion the reactor's completion.
 */
@UtilityClass
final class ReportProjects {

    /**
     * The profile that applies what the ordinary build only reports, and so edits the sources it judges.
     */
    static final String FORMAT = "format";
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

    /**
     * Whether the invocation itself selected the format profile, with {@code -Pformat} or the active profiles of
     * the Maven settings, and did not deselect it.
     *
     * <p>Only the invocation can switch the tree comparisons off. A profile of that id in a project's own model,
     * active by default or by a condition that always holds, is active for the project without anyone having
     * asked for formatting, so it never answers this question. The model policy refuses such a profile as well.
     *
     * @param request the execution request of the invocation
     * @return whether the invocation selected the format profile
     */
    static boolean formatting(MavenExecutionRequest request) {
        return request.getActiveProfiles().contains(FORMAT) && !request.getInactiveProfiles().contains(FORMAT);
    }

    static MavenProject root(List<MavenProject> projects) {
        return projects.stream().filter(MavenProject::isExecutionRoot).findFirst().orElseGet(projects::getFirst);
    }

    static List<MavenProject> required(List<MavenProject> projects) {
        return projects.stream().filter(project -> !AirnessCoordinates.isAggregator(project)).toList();
    }

    static List<String> qualifications(Collection<MavenProject> selected, boolean partial, boolean enforced) {
        return Stream.<Stream<String>>of(
            partial ? Stream.of("This invocation selected only part of the reactor.") : Stream.empty(),
            enforced ? Stream.empty()
                : Stream.of("Report-only mode does not establish an Airness verification pass."),
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
}
