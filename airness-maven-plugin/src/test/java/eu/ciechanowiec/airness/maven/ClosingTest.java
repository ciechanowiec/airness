package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.Findings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Closing waits for every module that completes, refuses to vouch for one that never will, and judges the
 * whole tree and the whole repository's suppressions.
 */
class ClosingTest {

    private static final Duration GIT_TIMEOUT = Duration.ofSeconds(60);

    @Test
    void awaitsAndCoversOnlyCompletingModulesAndWhatTheyDependOn() {
        MavenProject governed = project("governed", true);
        MavenProject library = project("library", false);
        MavenProject distribution = project("distribution", false);
        List<MavenProject> required = List.of(library, governed, distribution);
        assertEquals(Set.of(governed.getId()), Closing.awaited(required));
        List<MavenProject> covered = Closing.covered(
            required, project -> project.equals(governed) ? List.of(library) : List.of(governed, library)
        );
        assertEquals(List.of(library, governed), covered, "what a completing module depends on is finished first");
        assertEquals(
            List.of(
                distribution.getId() + " inherits no Airness parent and no module that completes depends on it, so"
                    + " closing does not cover it."
            ),
            Closing.uncovered(required, covered),
            "a module ordered last on a parent of its own is left out and named, never awaited"
        );
    }

    @Test
    void comparesTheWholeTree() {
        assertTrue(Closing.tree(true).clean());
        Findings moved = Closing.tree(false);
        assertTrue(moved.offences().getFirst().contains("snapshot the first module took at validate"));
    }

    @Test
    @SneakyThrows
    void readsOnlyTheCodeTheNodeProjectWrote(@TempDir Path root, @TempDir Path elsewhere) {
        git(root, "init", "--quiet");
        Path web = node(root.resolve("web"));
        List<Path> sources = Closing.nodeSources(root, List.of(web), List.of("web/src/pinned.ts"));
        assertEquals(
            List.of(web.resolve("src/view.ts"), web.resolve("tools/build.ts")),
            sources.stream().sorted().toList(),
            "source roots and declared roots only, never tests, specs, output, generated or pinned files"
        );
        Path outside = node(elsewhere.resolve("web"));
        assertEquals(
            3, Closing.nodeSources(root, List.of(outside), List.of()).size(),
            "a node project git cannot list is walked instead, and its pinned file is no longer excluded"
        );
        assertEquals(0, Closing.javaSuppressions(root, List.of()).scanned());
    }

    @SneakyThrows
    private static Path node(Path web) {
        for (String directory : List.of("node_modules/x", "dist", "src/generated", "src/deep", "tests", "tools")) {
            Files.createDirectories(web.resolve(directory));
        }
        Files.writeString(
            web.resolve("package.json"),
            "{\"airness\": {\"sourceRoots\": [\"tools\"],"
                + " \"generatedArtefacts\": [{\"pattern\": \"src/generated/**\"}]}}"
        );
        Files.writeString(web.resolve("src/view.ts"), "// eslint-disable-next-line no-console\nconsole.log(1)\n");
        Files.writeString(web.resolve("src/pinned.ts"), "export {}\n");
        Files.writeString(web.resolve("src/deep/view.spec.ts"), "export {}\n");
        Files.writeString(web.resolve("src/generated/types.ts"), "/* eslint-disable */\n");
        Files.writeString(web.resolve("tests/view.ts"), "// @ts-ignore\n");
        Files.writeString(web.resolve("tools/build.ts"), "export {}\n");
        Files.writeString(web.resolve("eslint.config.mjs"), "export default []\n");
        Files.writeString(web.resolve("node_modules/x/index.js"), "// eslint-disable\n");
        Files.writeString(web.resolve("dist/view.js"), "// eslint-disable\n");
        return web;
    }

    @SneakyThrows
    private static void git(Path root, String... arguments) {
        List<String> command = Stream.concat(Stream.of("git", "-C", root.toString()), Stream.of(arguments)).toList();
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        if (!process.waitFor(GIT_TIMEOUT) || process.exitValue() != 0) {
            process.destroyForcibly();
            throw new IllegalStateException("Fixture Git command failed: " + command);
        }
    }

    private static MavenProject project(String name, boolean completes) {
        Plugin plugin = new Plugin();
        plugin.setGroupId("eu.ciechanowiec");
        plugin.setArtifactId("airness-maven-plugin");
        PluginExecution execution = new PluginExecution();
        execution.setId("airness-module-complete");
        execution.addGoal(completes ? CompletionOrder.GOAL : "tree-verify");
        plugin.addExecution(execution);
        Build build = new Build();
        build.addPlugin(plugin);
        Model model = new Model();
        model.setGroupId("com.example");
        model.setArtifactId(name);
        model.setVersion("1");
        model.setPackaging("pom");
        model.setBuild(build);
        return new MavenProject(model);
    }

    @Test
    @SneakyThrows
    void readsAMemberWithoutOrWithAnUnreadableManifestByTheDefaultRoots(@TempDir Path root) {
        Path bare = Files.createDirectories(root.resolve("bare/src"));
        Files.writeString(bare.resolve("view.ts"), "export {}\n");
        Path broken = Files.createDirectories(root.resolve("broken/src"));
        Files.writeString(root.resolve("broken/package.json"), "{");
        Files.writeString(broken.resolve("view.ts"), "export {}\n");
        Path blank = Files.createDirectories(root.resolve("blank/src"));
        Files.writeString(
            root.resolve("blank/package.json"), "{\"airness\": {\"generatedArtefacts\": [{\"pattern\": \" \"}]}}"
        );
        Files.writeString(blank.resolve("view.ts"), "export {}\n");
        assertEquals(
            3, Closing.nodeSources(
                root, List.of(root.resolve("bare"), root.resolve("broken"), root.resolve("blank")),
                List.of()
            ).size(),
            "src stays a source root whatever the manifest says, and a blank glob excludes nothing"
        );
    }
}
