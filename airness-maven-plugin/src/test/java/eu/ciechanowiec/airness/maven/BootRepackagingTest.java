package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.junit.jupiter.api.Test;

/**
 * Which module of a reactor becomes the deployable archive is stated by the binding that builds it, so
 * that is what is read. A plugin present with no execution is the pinned version Airness supplies and
 * says nothing about which module the project meant to deploy.
 */
class BootRepackagingTest {

    private static final String BOOT = "org.springframework.boot";
    private static final String PLUGIN = "spring-boot-maven-plugin";

    private static Plugin plugin(String group, String artifact, List<String> goals) {
        Plugin declared = new Plugin();
        declared.setGroupId(group);
        declared.setArtifactId(artifact);
        PluginExecution execution = new PluginExecution();
        execution.setGoals(goals);
        declared.setExecutions(List.of(execution));
        return declared;
    }

    @Test
    void readsABoundRepackageGoalAsTheDeployableModule() {
        assertTrue(
            BootRepackaging.applies(List.of(plugin(BOOT, PLUGIN, List.of("repackage")))),
            "the binding that builds the archive is what says which module is deployed"
        );
    }

    @Test
    void leavesAModuleThatBindsAnotherGoalOfThePlugin() {
        assertFalse(
            BootRepackaging.applies(List.of(plugin(BOOT, PLUGIN, List.of("build-info")))),
            "recording build information leaves the module the library it was"
        );
    }

    @Test
    void leavesAPluginOfAnotherGroupNamedTheSame() {
        assertFalse(
            BootRepackaging.applies(List.of(plugin("com.example", PLUGIN, List.of("repackage")))),
            "a repackage goal is only Boot's where the plugin is Boot's"
        );
    }

    @Test
    void leavesAnotherPluginOfTheSameGroup() {
        assertFalse(
            BootRepackaging.applies(List.of(plugin(BOOT, "spring-boot-antlib", List.of("repackage")))),
            "the group alone does not make a plugin the one that builds the archive"
        );
    }

    @Test
    void leavesAModuleThatBindsNothing() {
        assertFalse(BootRepackaging.applies(List.of()), "a module binding no plugin deploys nothing");
    }
}
