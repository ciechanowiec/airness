package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;

/**
 * The gate ids a module's bound node-gate executions own are read from its effective build.
 */
class NodeGateOwnershipTest {

    private static final String GROUP = "eu.ciechanowiec";

    @Test
    void readsTheGateOfEveryBoundNodeGateExecutionOfTheAirnessPlugin() {
        Plugin airness = plugin(
            "airness-maven-plugin",
            execution("node-gate", Optional.of("types")),
            execution("node-gate", Optional.empty()),
            execution("node-run", Optional.of("build"))
        );
        Plugin other = plugin("other-plugin", execution("node-gate", Optional.of("eslint")));
        Plugin foreign = plugin("airness-maven-plugin", execution("node-gate", Optional.of("biome")));
        foreign.setGroupId("org.example");
        assertEquals(
            Set.of("types"), NodeGateOwnership.bound(List.of(airness, other, foreign)),
            "only a node-gate execution of the Airness plugin that names a gate owns it"
        );
    }

    private static Plugin plugin(String artifact, PluginExecution... executions) {
        Plugin plugin = new Plugin();
        plugin.setGroupId(GROUP);
        plugin.setArtifactId(artifact);
        plugin.setExecutions(List.of(executions));
        return plugin;
    }

    private static PluginExecution execution(String goal, Optional<String> gate) {
        PluginExecution execution = new PluginExecution();
        execution.setGoals(List.of(goal));
        gate.ifPresent(id -> execution.setConfiguration(configuration(id)));
        return execution;
    }

    private static Xpp3Dom configuration(String gate) {
        Xpp3Dom configuration = new Xpp3Dom("configuration");
        Xpp3Dom child = new Xpp3Dom("gate");
        child.setValue(" " + gate + " ");
        configuration.addChild(child);
        return configuration;
    }
}
