package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.maven.NodeProjects.NodeProject;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A declaration resolves to node projects keyed by their path in the module, and the module's bindings say
 * whether it builds a frontend into its artifact.
 */
class NodeProjectsTest {

    private static final String FRONTEND = "src/main/frontend";

    @TempDir
    private Path module;

    @Test
    void keysEachDeclaredDirectoryByItsNormalizedPathInTheModule() {
        NodeProjects projects = NodeProjects.of(
            this.module, " src/main/frontend/ , ., ui/./storefront,src/main/frontend,, "
        );
        assertEquals(
            List.of(FRONTEND, ".", "ui/storefront"),
            projects.projects().stream().map(NodeProject::key).toList(),
            "repeats and blanks fall away, and the module itself is ."
        );
        assertEquals(this.module.resolve(FRONTEND), projects.projects().getFirst().directory());
        assertTrue(NodeProjects.of(this.module, null).none());
        assertTrue(NodeProjects.of(this.module, "  ").none());
    }

    @Test
    void findsADeclaredProjectHoweverAnExecutionWritesIt() {
        NodeProjects projects = NodeProjects.of(this.module, FRONTEND);
        assertEquals(Optional.of(FRONTEND), projects.find(this.module, "./src/main/frontend/").map(NodeProject::key));
        assertEquals(Optional.empty(), projects.find(this.module, "ui"));
    }

    @Test
    void tellsADirectoryOutsideTheModuleApart() {
        List<NodeProject> projects = NodeProjects.of(this.module, "../web, ..,web/..x").projects();
        assertEquals(List.of(true, true, false), projects.stream().map(NodeProject::outside).toList());
    }

    @Test
    @SneakyThrows
    void readsTheManifestLockfileAndScriptsOfAProject() {
        NodeProject project = NodeProjects.of(this.module, ".").projects().getFirst();
        assertFalse(project.hasManifest() || project.hasLockfile());
        assertThrows(UncheckedIOException.class, () -> project.declaresScript("build"));
        Files.writeString(this.module.resolve("package.json"), "{\"scripts\": {\"build\": \"vite build\"}}");
        Files.writeString(this.module.resolve("pnpm-lock.yaml"), "lockfileVersion: '9.0'\n");
        assertTrue(project.hasManifest() && project.hasLockfile());
        assertTrue(project.declaresScript("build"));
        assertFalse(project.declaresScript("serve"));
    }

    @Test
    @SneakyThrows
    void letsNodeRunRunOnlyADeclaredScriptOfADeclaredProject() {
        NodeProjects projects = NodeProjects.of(this.module, FRONTEND);
        IllegalArgumentException missing = assertThrows(
            IllegalArgumentException.class, () -> projects.runnable(this.module, FRONTEND, "build")
        );
        assertTrue(String.valueOf(missing.getMessage()).contains("has no package.json"), missing.getMessage());
        Files.createDirectories(this.module.resolve(FRONTEND));
        Files.writeString(
            this.module.resolve(FRONTEND).resolve("package.json"), "{\"scripts\": {\"build\": \"vite\"}}"
        );
        assertEquals(FRONTEND, projects.runnable(this.module, FRONTEND, "build").key());
        IllegalArgumentException undeclared = assertThrows(
            IllegalArgumentException.class, () -> projects.runnable(this.module, "ui", "build")
        );
        assertTrue(String.valueOf(undeclared.getMessage()).contains("is not listed in airness.node.projects"));
        IllegalArgumentException unknown = assertThrows(
            IllegalArgumentException.class, () -> projects.runnable(this.module, FRONTEND, "deploy")
        );
        assertTrue(String.valueOf(unknown.getMessage()).contains("declares no script deploy"), unknown.getMessage());
    }

    @Test
    void placesResultsBeneathTheBuildDirectoryByKey() {
        Path build = this.module.resolve("target");
        List<NodeProject> projects = NodeProjects.of(this.module, "., ui/storefront").projects();
        assertEquals(build.resolve("airness/node/module"), projects.getFirst().results(build));
        assertEquals(build.resolve("airness/node/projects/ui/storefront"), projects.getLast().results(build));
        assertEquals(build.resolve("airness/node-logs/module"), projects.getFirst().logs(build));
        assertEquals(build.resolve("airness/node-logs/projects/ui/storefront"), projects.getLast().logs(build));
    }

    @Test
    void recognizesAModuleThatBuildsItsFrontendWithNodeRun() {
        Plugin exec = other("org.codehaus.mojo", "exec-maven-plugin");
        Plugin sibling = other("eu.ciechanowiec", "airness-other-plugin");
        assertTrue(NodeProjects.buildsWithNodeRun(List.of(exec, airness("node-gate", "node-run"))));
        assertFalse(NodeProjects.buildsWithNodeRun(List.of(exec, sibling, airness("node-gate"))));
        assertFalse(NodeProjects.buildsWithNodeRun(List.of()));
    }

    private static Plugin airness(String... goals) {
        Plugin plugin = new Plugin();
        plugin.setGroupId("eu.ciechanowiec");
        plugin.setArtifactId("airness-maven-plugin");
        PluginExecution execution = new PluginExecution();
        execution.setGoals(List.of(goals));
        plugin.addExecution(execution);
        return plugin;
    }

    private static Plugin other(String group, String artifact) {
        Plugin plugin = new Plugin();
        plugin.setGroupId(group);
        plugin.setArtifactId(artifact);
        PluginExecution execution = new PluginExecution();
        execution.setGoals(List.of("node-run"));
        plugin.addExecution(execution);
        return plugin;
    }
}
