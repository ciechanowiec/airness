package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A setting that lets pnpm pick a pnpm or a Node of its own is refused wherever a node project writes it.
 */
class NodeSelectionsTest {

    private static final String MANIFEST = "package.json";
    private static final String REPAIR = " lets pnpm choose a pnpm or Node of its own; remove it, since Airness "
        + "supplies both";

    @TempDir
    private Path module;

    @Test
    @SneakyThrows
    void refusesEverySettingThatSelectsAPnpmOrANode() {
        Path ui = Files.createDirectories(this.module.resolve("ui"));
        Path member = Files.createDirectories(ui.resolve("packages/theme"));
        Files.writeString(ui.resolve(MANIFEST), "{\"packageManager\": \"pnpm@11.0.0\", \"devEngines\": {}}");
        Files.writeString(member.resolve(MANIFEST), "{\"pnpm\": {\"executionEnv\": {\"nodeVersion\": \"20\"}}}");
        Files.writeString(ui.resolve("pnpm-workspace.yaml"), "packages:\n  - packages/*\nuseNodeVersion: 20.0.0\n");
        Files.writeString(ui.resolve(".npmrc"), "registry=https://example.invalid\nuse-node-version = 20.0.0\n");
        Path broken = Files.createDirectories(this.module.resolve("broken"));
        Files.writeString(broken.resolve(MANIFEST), "{not json");
        List<String> problems = NodeSelections.problems(
            this.module,
            List.of(ui.resolve(MANIFEST), member.resolve(MANIFEST)),
            NodeProjects.of(this.module, "ui,../outside")
        ).toList();
        assertEquals(
            List.of(
                "ui/package.json: devEngines" + REPAIR,
                "ui/package.json: packageManager" + REPAIR,
                "ui/packages/theme/package.json: pnpm.executionEnv" + REPAIR,
                "ui/pnpm-workspace.yaml: useNodeVersion" + REPAIR,
                "ui/.npmrc: use-node-version" + REPAIR
            ),
            problems
        );
        List<String> unreadable = NodeSelections.problems(
            this.module, List.of(broken.resolve(MANIFEST)), NodeProjects.of(this.module, null)
        ).toList();
        assertEquals(1, unreadable.size());
        assertTrue(
            unreadable.getFirst().startsWith("broken/package.json: cannot be read as JSON"), unreadable.getFirst()
        );
    }

    @Test
    @SneakyThrows
    void acceptsAProjectThatLeavesTheToolchainToTheHarness() {
        Path ui = Files.createDirectories(this.module.resolve("ui"));
        Files.writeString(ui.resolve(MANIFEST), "{\"engines\": {\"node\": \">=26\"}}");
        Files.writeString(ui.resolve("pnpm-workspace.yaml"), "- not a mapping\n");
        assertEquals(
            List.of(), NodeSelections.problems(
                this.module, List.of(ui.resolve(MANIFEST)), NodeProjects.of(this.module, "ui")
            ).toList()
        );
        Files.writeString(ui.resolve("pnpm-workspace.yaml"), "packages: [unclosed\n");
        assertEquals(
            List.of("ui/pnpm-workspace.yaml: cannot be read as YAML"),
            NodeSelections.problems(this.module, List.of(), NodeProjects.of(this.module, "ui")).toList()
        );
    }
}
