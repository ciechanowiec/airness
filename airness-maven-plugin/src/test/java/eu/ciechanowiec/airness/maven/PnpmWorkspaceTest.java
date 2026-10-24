package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A workspace root covers the members its globs admit and its negated globs do not exclude.
 */
class PnpmWorkspaceTest {

    @TempDir
    private Path root;

    @SneakyThrows
    private PnpmWorkspace workspace(String yaml) {
        Files.writeString(this.root.resolve("pnpm-workspace.yaml"), yaml);
        return PnpmWorkspace.at(this.root).orElseThrow();
    }

    @Test
    void readsNoWorkspaceWhereNoWorkspaceFileIs() {
        assertEquals(Optional.empty(), PnpmWorkspace.at(this.root));
    }

    @Test
    @SneakyThrows
    void coversWhatTheGlobsAdmitAndNotWhatANegationExcludes() {
        PnpmWorkspace workspace = this.workspace(
            "packages:\n  - ./apps/*\n  - 'libs/**/'\n  - '!libs/legacy/**'\n  - ''\n"
        );
        assertTrue(workspace.covers(this.root.resolve("apps/web")));
        assertTrue(workspace.covers(this.root.resolve("libs/ui/buttons")));
        assertFalse(workspace.covers(this.root.resolve("apps/web/nested")), "a single star stays in one directory");
        assertFalse(workspace.covers(this.root.resolve("libs/legacy/old")), "the negation excludes it");
        assertFalse(workspace.covers(this.root.resolve("tools")), "nothing admits it");
        assertFalse(workspace.covers(this.root), "the root is the declared project, not a member of itself");
    }

    @Test
    @SneakyThrows
    void coversNothingWhenTheFileListsNoPackages() {
        assertFalse(this.workspace("onlyBuiltDependencies:\n  - esbuild\n").covers(this.root.resolve("apps/web")));
        assertFalse(this.workspace("- a list\n").covers(this.root.resolve("a")));
    }

    @Test
    @SneakyThrows
    void refusesAWorkspaceFileThatIsNotYaml() {
        Files.writeString(this.root.resolve("pnpm-workspace.yaml"), "packages: [unclosed\n");
        assertThrows(IllegalStateException.class, () -> PnpmWorkspace.at(this.root));
    }

    @Test
    @SneakyThrows
    void letsALeadingOrTrailingGlobstarMatchNoDirectoryAsPnpmDoes() {
        PnpmWorkspace workspace = this.workspace(
            """
                packages:
                  - 'packages/**'
                  - '!**/test/**'
                """
        );
        assertTrue(workspace.covers(this.root.resolve("packages/ui")));
        assertTrue(workspace.covers(this.root.resolve("packages")), "a trailing globstar matches no directory");
        assertFalse(
            workspace.covers(this.root.resolve("packages/test")),
            "the exclusion reaches the test directory itself, as pnpm's does, so it is not counted as covered"
        );
        assertFalse(workspace.covers(this.root.resolve("test")), "a leading globstar matches no directory");
    }

    @Test
    @SneakyThrows
    void refusesAGlobWhoseSyntaxReadsDifferentlyInPnpm() {
        for (String glob : List.of("'packages/!(legacy)'", "'packages/[!a]*'", "'!packages/@(x|y)'")) {
            Files.writeString(this.root.resolve("pnpm-workspace.yaml"), "packages:\n  - " + glob + "\n");
            assertThrows(IllegalStateException.class, () -> PnpmWorkspace.at(this.root), glob);
        }
    }
}
