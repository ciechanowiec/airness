package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

class AirnessCoordinatesTest {

    private static final String GROUP = "eu.ciechanowiec";
    private static final String OTHER_GROUP = "com.example";
    private static final String GENERIC_PARENT = "airness-parent";
    private static final String JAVA_PARENT = "airness-parent-java";
    private static final String PLUGIN = "airness-maven-plugin";
    private static final String COMPLETION = "module-complete";

    @Test
    void namesTheAirnessArtifactOnlyUnderItsOwnGroup() {
        assertTrue(AirnessCoordinates.named(project(GROUP, GENERIC_PARENT), GENERIC_PARENT));
        assertFalse(AirnessCoordinates.named(project(OTHER_GROUP, GENERIC_PARENT), GENERIC_PARENT));
        assertFalse(AirnessCoordinates.named(project(GROUP, JAVA_PARENT), GENERIC_PARENT));
    }

    @Test
    void recognizesTheAggregatorOnlyUnderItsOwnGroup() {
        assertTrue(AirnessCoordinates.isAggregator(project(GROUP, "airness")));
        assertFalse(AirnessCoordinates.isAggregator(project(OTHER_GROUP, "airness")));
        assertFalse(AirnessCoordinates.isAggregator(project(GROUP, GENERIC_PARENT)));
    }

    @Test
    void findsTheNearestAncestorWithTheArtifactIdBeyondAConsumerAggregator() {
        MavenProject far = project(GROUP, GENERIC_PARENT);
        MavenProject near = project(GROUP, GENERIC_PARENT);
        MavenProject aggregator = project(OTHER_GROUP, "aggregator");
        MavenProject module = project(OTHER_GROUP, "module");
        near.setParent(far);
        aggregator.setParent(near);
        module.setParent(aggregator);
        assertSame(near, AirnessCoordinates.ancestor(module, GENERIC_PARENT).orElseThrow());
        assertTrue(AirnessCoordinates.inherits(module, GENERIC_PARENT));
    }

    @Test
    void neverCountsTheModuleItselfAsItsOwnAncestor() {
        MavenProject parent = project(GROUP, GENERIC_PARENT);
        assertEquals(Optional.empty(), AirnessCoordinates.ancestor(parent, GENERIC_PARENT));
        assertFalse(AirnessCoordinates.inherits(parent, GENERIC_PARENT));
    }

    @Test
    void readsASameNamedParentOfAnotherGroupAsNoAirnessParent() {
        MavenProject foreign = project(OTHER_GROUP, JAVA_PARENT);
        MavenProject module = project(OTHER_GROUP, "module");
        module.setParent(foreign);
        assertFalse(AirnessCoordinates.inherits(module, JAVA_PARENT));
    }

    @Test
    void recognizesThePluginOnlyByItsGroupAndArtifactId() {
        assertTrue(AirnessCoordinates.isPlugin(plugin(GROUP, PLUGIN)));
        assertFalse(AirnessCoordinates.isPlugin(plugin(OTHER_GROUP, PLUGIN)));
        assertFalse(AirnessCoordinates.isPlugin(plugin(GROUP, "airness-other-plugin")));
        assertEquals("eu.ciechanowiec:airness-maven-plugin", AirnessCoordinates.PLUGIN_KEY);
    }

    @Test
    void findsAGoalOnlyInAnExecutionOfTheAirnessPlugin() {
        Plugin airness = plugin(GROUP, PLUGIN, "node-gate", COMPLETION);
        Plugin sibling = plugin(GROUP, "airness-other-plugin", "node-run");
        assertTrue(AirnessCoordinates.bindsGoal(List.of(sibling, airness), COMPLETION));
        assertFalse(AirnessCoordinates.bindsGoal(List.of(sibling, airness), "node-run"));
        assertFalse(AirnessCoordinates.bindsGoal(List.of(), COMPLETION));
    }

    private static MavenProject project(String group, String artifact) {
        MavenProject project = new MavenProject();
        project.setGroupId(group);
        project.setArtifactId(artifact);
        return project;
    }

    private static Plugin plugin(String group, String artifact, String... goals) {
        Plugin plugin = new Plugin();
        plugin.setGroupId(group);
        plugin.setArtifactId(artifact);
        PluginExecution execution = new PluginExecution();
        execution.setGoals(List.of(goals));
        plugin.addExecution(execution);
        return plugin;
    }
}
