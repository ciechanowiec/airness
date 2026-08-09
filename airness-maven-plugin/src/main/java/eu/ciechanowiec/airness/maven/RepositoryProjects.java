package eu.ciechanowiec.airness.maven;

import java.util.Optional;
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

    static boolean owns(MavenProject topLevel, MavenProject current) {
        return topLevel.equals(current) || selfBuild(topLevel, current);
    }

    static boolean selfBuild(MavenProject topLevel, MavenProject current) {
        return coordinates(current, CONSUMER_PARENT)
            && hasAirnessParent(current)
            && isAirnessEntry(topLevel, current);
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
