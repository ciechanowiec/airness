package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.AssetLocation;
import eu.ciechanowiec.airness.governance.AssetSite;
import eu.ciechanowiec.airness.governance.Layer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The files a node project declares generated in its own manifest are denied to the agent runtime, placed in the
 * project's directory, as the node gates honour them: with a pattern and a reason.
 */
class NodeGeneratedArtefactsTest {

    private static final String MANIFEST = "package.json";

    @TempDir
    private Path root;

    @Test
    @SneakyThrows
    void deniesWhatEachNodeProjectDeclaresGenerated() {
        Files.createDirectories(this.root.resolve("web"));
        Files.writeString(
            this.root.resolve("web").resolve(MANIFEST), """
                {"airness": {"generatedArtefacts": [
                  {"pattern": "src/generated/**", "reason": "the API client generator writes it"},
                  {"pattern": "src/unreasoned.ts"},
                  "src/bare.ts",
                  {"pattern": "../outside.ts", "reason": "outside the project"}
                ]}}
                """
        );
        Files.createDirectories(this.root.resolve("broken"));
        Files.writeString(this.root.resolve("broken").resolve(MANIFEST), "{");
        List<AssetSite> sites = List.of(
            AssetSite.root(Set.of(Layer.NODE)),
            new AssetSite("web", AssetLocation.NODE_PROJECT, Set.of(Layer.NODE)),
            new AssetSite("broken", AssetLocation.NODE_PROJECT, Set.of(Layer.NODE)),
            new AssetSite("absent", AssetLocation.NODE_PROJECT, Set.of(Layer.NODE))
        );
        assertEquals(List.of("web/src/generated/**"), NodeGeneratedArtefacts.paths(this.root, sites));
        List<String> denied = GeneratedDenialMojo.denied(this.root, sites, List.of("docs/api.md"), List.of());
        assertTrue(denied.contains("web/src/generated/**"), "the node setting reaches the denial: " + denied);
        assertTrue(denied.contains("docs/api.md"), "beside the Maven property");
    }

    @Test
    @SneakyThrows
    void deniesWhatEachWorkspaceMemberDeclaresGeneratedInItsOwnDirectory() {
        Path project = Files.createDirectories(this.root.resolve("app"));
        Files.writeString(project.resolve("pnpm-workspace.yaml"), "packages:\n  - members/*\n  - '!members/skipped'\n");
        Files.writeString(
            project.resolve(MANIFEST),
            "{\"airness\": {\"generatedArtefacts\": [{\"pattern\": \"root.gen.ts\", \"reason\": \"a generator\"}]}}"
        );
        String member
            = "{\"airness\": {\"generatedArtefacts\": [{\"pattern\": \"src/types.ts\", \"reason\": \"a generator\"}]}}";
        Files.createDirectories(project.resolve("members/alpha"));
        Files.writeString(project.resolve("members/alpha").resolve(MANIFEST), member);
        Files.createDirectories(project.resolve("members/skipped"));
        Files.writeString(project.resolve("members/skipped").resolve(MANIFEST), member);
        Files.createDirectories(project.resolve("node_modules/members/beta"));
        Files.writeString(project.resolve("node_modules/members/beta").resolve(MANIFEST), member);
        List<AssetSite> sites = List.of(new AssetSite("app", AssetLocation.NODE_PROJECT, Set.of(Layer.NODE)));
        assertEquals(
            List.of("app/root.gen.ts", "app/members/alpha/src/types.ts"), NodeGeneratedArtefacts.paths(this.root, sites)
        );
    }
}
