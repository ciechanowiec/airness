package eu.ciechanowiec.airness.maven;

import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.apache.maven.project.MavenProject;

/**
 * Selects the project that owns repository-wide work in an ordinary consumer and in Airness itself.
 */
@UtilityClass
final class RepositoryProjects {

    private static final String GROUP = "eu.ciechanowiec";
    private static final String AGGREGATOR = "airness";
    private static final String CONSUMER_PARENT = "airness-parent";
    /*
     * Every parent Airness publishes for a consumer to inherit. Each one declares what a consumer may not,
     * so the child policy that reads a consumer pom passes over all of them. Kept apart from
     * CONSUMER_PARENT above, which selects the one module that owns repository-wide work and stays one:
     * a second owner would report every repository finding twice.
     */
    private static final Set<String> HARNESS_PARENTS = Set.of(CONSUMER_PARENT, "airness-parent-spring-boot");

    static boolean owns(MavenProject topLevel, MavenProject current) {
        return topLevel.equals(current) || selfBuild(topLevel, current);
    }

    static boolean selfBuild(MavenProject topLevel, MavenProject current) {
        return coordinates(current, CONSUMER_PARENT)
            && hasAirnessParent(current)
            && isAirnessEntry(topLevel, current);
    }

    static boolean harnessParent(MavenProject project) {
        return GROUP.equals(project.getGroupId()) && HARNESS_PARENTS.contains(project.getArtifactId());
    }

    private static boolean coordinates(MavenProject project, String artifact) {
        return GROUP.equals(project.getGroupId()) && artifact.equals(project.getArtifactId());
    }

    private static boolean hasAirnessParent(MavenProject project) {
        return Optional.ofNullable(project.getParent())
            .filter(parent -> coordinates(parent, AGGREGATOR))
            .isPresent();
    }

    private static boolean isAirnessEntry(MavenProject topLevel, MavenProject current) {
        return topLevel.equals(current) || coordinates(topLevel, AGGREGATOR);
    }
}
