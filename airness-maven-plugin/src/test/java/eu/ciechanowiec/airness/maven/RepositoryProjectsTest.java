package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

class RepositoryProjectsTest {

    @Test
    void selectsAnOrdinaryTopLevelProject() {
        MavenProject project = project("sample", "application");

        assertTrue(RepositoryProjects.owns(project, project));
    }

    @Test
    void selectsTheConsumerParentDuringTheAirnessSelfBuild() {
        MavenProject root = project("eu.ciechanowiec", "airness");
        MavenProject parent = project("eu.ciechanowiec", "airness-parent");
        parent.setParent(root);

        assertTrue(RepositoryProjects.owns(root, parent));
    }

    @Test
    void recognizesTheConsumerParentBuiltOutsideTheReactor() {
        MavenProject root = project("eu.ciechanowiec", "airness");
        MavenProject parent = project("eu.ciechanowiec", "airness-parent");
        parent.setParent(root);

        assertTrue(RepositoryProjects.selfBuild(parent, parent));
    }

    @Test
    void rejectsAnOrdinaryChildModule() {
        MavenProject root = project("sample", "application");
        MavenProject child = project("sample", "module");

        assertFalse(RepositoryProjects.owns(root, child));
    }

    @Test
    void rejectsAnAirnessGroupBuildWithAnotherAggregator() {
        MavenProject root = project("eu.ciechanowiec", "another-aggregator");
        MavenProject parent = project("eu.ciechanowiec", "airness-parent");
        parent.setParent(root);

        assertFalse(RepositoryProjects.owns(root, parent));
    }

    private static MavenProject project(String group, String artifact) {
        MavenProject project = new MavenProject();
        project.setGroupId(group);
        project.setArtifactId(artifact);
        return project;
    }
}
