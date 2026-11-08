package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.BuildInput;
import eu.ciechanowiec.airness.governance.IgnoredBuildInputs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The node toolchain reads every script and stylesheet under a checked source root from disk, so an ignore
 * pattern that happens to match one is refused before the file goes missing from a commit.
 */
class NodeBuildInputsTest {

    private static final String MODULE = "shop";
    private static final String MANIFEST = "package.json";
    private static final String WHOLE = "{\"name\":\"web\",\"airness\":{\"sourceRoots\":[\".\"]}}";

    @TempDir
    private Path module;

    @Test
    void selectsScriptsAndStylesheetsUnderEveryExistingCheckedRoot() {
        this.write("web/" + MANIFEST, "{\"name\":\"web\"}");
        this.write("web/src/app/build/page.tsx");
        this.write("web/src/theme.css");
        this.write("web/src/notes.md");
        this.write("web/src/node_modules/kit/index.js");
        this.write("web/tests/page.spec.ts");
        this.write("web/lib/outside.ts");
        assertEquals(
            Map.of("web/src", Set.of("app/build/page.tsx", "theme.css"), "web/tests", Set.of("page.spec.ts")),
            this.selected("web"),
            "a directory named build below a root is source; installed packages and other files are not read"
        );
    }

    @Test
    void passesOverToolOutputAtTheTopOfAWholeProjectRootAndGeneratedArtefacts() {
        this.write(
            MANIFEST,
            "{\"name\":\"web\",\"airness\":{\"sourceRoots\":[\".\"],"
                + "\"generatedArtefacts\":[{\"pattern\":\"src/generated-types.ts\",\"reason\":\"generated\"}]}}"
        );
        this.write("next-env.d.ts");
        this.write("dist/index.js");
        this.write(".next/server/page.js");
        this.write("coverage/lcov-report/sorter.js");
        this.write("src/generated-types.ts");
        this.write("src/build/index.ts");
        this.write("vitest.config.mts");
        assertEquals(
            Set.of("src/build/index.ts", "vitest.config.mts"), this.selected(".").get("."),
            "only the project's own sources are inputs"
        );
    }

    @Test
    @SneakyThrows
    void readsALinkAsNoSourceAndNextTypesBelowTheTopAsOne() {
        this.write(MANIFEST, WHOLE);
        this.write("src/next-env.d.ts");
        this.write("src/real.ts");
        Files.createSymbolicLink(this.module.resolve("src/linked.ts"), this.module.resolve("src/real.ts"));
        assertEquals(
            Set.of("src/next-env.d.ts", "src/real.ts"), this.selected(".").get("."),
            "a link is read through its target, and only the Next.js types at the top are generated"
        );
    }

    @Test
    void readsNothingForADeclaredProjectOutsideTheModule() {
        this.write("web/" + MANIFEST, "{\"name\":\"web\"}");
        this.write("web/src/index.ts");
        assertEquals(
            List.of(), NodeBuildInputs.of(MODULE, NodeProjects.of(this.module.resolve("shop"), "../web")),
            "a project outside the module is no module's build input"
        );
    }

    @Test
    void readsNothingForADeclaredDirectoryWithoutAManifest() {
        this.write("web/src/index.ts");
        assertEquals(Map.of(), this.selected("web"), "a directory without a manifest is not yet a node project");
    }

    @Test
    @SneakyThrows
    void refusesASourceAnUnanchoredIgnorePatternHides() {
        Path root = this.module.toRealPath();
        Process init = new ProcessBuilder("git", "init", "--quiet").directory(root.toFile()).start();
        assertEquals(0, init.waitFor(), "git init");
        this.write(".gitignore", "*.log*\nbuild/\n");
        this.write(MANIFEST, WHOLE);
        this.write("build/index.js");
        this.write("src/server/http.logger.ts");
        this.write("src/server/http.ts");
        List<BuildInput> inputs = NodeBuildInputs.of(MODULE, NodeProjects.of(root, "."));
        List<String> problems = new IgnoredBuildInputs(root, inputs, List.of()).problems();
        assertEquals(1, problems.size(), String.valueOf(problems));
        assertTrue(
            problems.getFirst().startsWith("shop node source: ignored build input src/server/http.logger.ts"),
            problems.getFirst()
        );
    }

    private Map<String, Set<String>> selected(String declared) {
        return NodeBuildInputs.of(MODULE, NodeProjects.of(this.module, declared)).stream().collect(
            Collectors.toMap(
                input -> relative(this.module, input.directory()), BuildInput::selected
            )
        );
    }

    private static String relative(Path module, Path directory) {
        String name = module.toAbsolutePath().normalize().relativize(directory).toString();
        return name.isEmpty() ? "." : name;
    }

    private void write(String path) {
        this.write(path, "export {};\n");
    }

    private void write(String path, String content) {
        Path file = this.module.resolve(path);
        try {
            Files.createDirectories(file.resolveSibling(""));
            Files.writeString(file, content);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
