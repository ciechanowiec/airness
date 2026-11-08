package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SelfTreeTest {

    private static final String GROUP = "eu.ciechanowiec";
    private static final String AGGREGATOR = "airness";
    private static final String CONSUMER_PARENT = "airness-parent";

    /*
     * A build resumed with -rf :airness-node makes that module its own top-level project, so the session
     * says nothing of the aggregator. The parent chain still reaches it at the root of the working tree.
     */
    @Test
    void recognizesAirnessItselfFromAModuleAResumedBuildStartsAt(@TempDir Path tree) {
        MavenProject parent = located(project(GROUP, CONSUMER_PARENT), tree.resolve(CONSUMER_PARENT));
        parent.setParent(located(project(GROUP, AGGREGATOR), tree));
        MavenProject node = located(project(GROUP, "airness-node"), tree.resolve("airness-node"));
        node.setParent(parent);

        assertTrue(RepositoryProjects.selfTree(node, tree));
    }

    @Test
    void recognizesAConsumerAsAnotherTreeThoughItDescendsFromTheAggregator(@TempDir Path tree) {
        Path local = tree.resolve("local-repository");
        MavenProject parent = located(project(GROUP, CONSUMER_PARENT), local.resolve(CONSUMER_PARENT));
        parent.setParent(located(project(GROUP, AGGREGATOR), local.resolve(AGGREGATOR)));
        MavenProject consumer = located(project("sample", "application"), tree);
        consumer.setParent(parent);

        assertFalse(RepositoryProjects.selfTree(consumer, tree));
    }

    @Test
    void recognizesAProjectWithoutTheAggregatorAsAnotherTree(@TempDir Path tree) {
        assertFalse(RepositoryProjects.selfTree(located(project("sample", "application"), tree), tree));
    }

    private static MavenProject located(MavenProject project, Path directory) {
        project.setFile(directory.resolve("pom.xml").toFile());
        return project;
    }

    private static MavenProject project(String group, String artifact) {
        MavenProject project = new MavenProject();
        project.setGroupId(group);
        project.setArtifactId(artifact);
        return project;
    }
}
