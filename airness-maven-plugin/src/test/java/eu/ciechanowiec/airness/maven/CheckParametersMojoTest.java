package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

class CheckParametersMojoTest {

    @Test
    void findsTheAirnessParentBeyondAReactorAggregator() {
        MavenProject airness = project("eu.ciechanowiec", "airness-parent", "1.2.3", null);
        MavenProject aggregator = project("sample", "aggregator", "4.5.6", airness);
        MavenProject module = project("sample", "module", "4.5.6", aggregator);

        assertEquals("1.2.3", CheckParametersMojo.airnessParentVersion(module).orElseThrow());
    }

    private static MavenProject project(
        String group, String artifact, String version, MavenProject parent
    ) {
        MavenProject project = new MavenProject();
        project.setGroupId(group);
        project.setArtifactId(artifact);
        project.setVersion(version);
        project.setParent(parent);
        return project;
    }
}
