package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

class CheckParametersMojoTest {

    @Test
    void findsTheAirnessParentBeyondAReactorAggregator() {
        MavenProject airness = project("eu.ciechanowiec", "airness-parent", "1.2.3", Optional.empty());
        MavenProject aggregator = project("sample", "aggregator", "4.5.6", Optional.of(airness));
        MavenProject module = project("sample", "module", "4.5.6", Optional.of(aggregator));

        assertEquals("1.2.3", CheckParametersMojo.airnessParentVersion(module).orElseThrow());
    }

    private static MavenProject project(
        String group, String artifact, String version, Optional<MavenProject> parent
    ) {
        MavenProject project = new MavenProject();
        project.setGroupId(group);
        project.setArtifactId(artifact);
        project.setVersion(version);
        parent.ifPresent(project::setParent);
        return project;
    }
}
