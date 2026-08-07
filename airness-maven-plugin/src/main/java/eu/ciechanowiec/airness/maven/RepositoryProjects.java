package eu.ciechanowiec.airness.maven;

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
        return GROUP.equals(topLevel.getGroupId())
            && AGGREGATOR.equals(topLevel.getArtifactId())
            && GROUP.equals(current.getGroupId())
            && CONSUMER_PARENT.equals(current.getArtifactId());
    }
}
