package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The sync writes a pinned file whenever it differs, writes a seed only when it is absent, deletes
 * nothing, and skips what the project has taken over.
 *
 * <p>The seed asymmetry is the point of having two policies rather than one. A seed's body belongs to
 * the project the moment it exists, so rewriting it would throw away the very content the policy exists
 * to allow.
 */
class AssetSyncTest {

    private static final String MANIFEST = """
        .editorconfig\tPINNED
        .gitignore\tSEED
        rewrite.yml\tFORBIDDEN
        """;

    private static final String CANONICAL = "root = true\n";
    private static final String SEEDED = "target/\n";

    @TempDir
    private Path shipped;

    private List<String> sync(Path root, String... unmanaged) {
        AssetCatalogue catalogue = new AssetFixture(this.shipped, MANIFEST)
            .ship(".editorconfig", CANONICAL)
            .ship(".gitignore", SEEDED)
            .catalogue();
        return new AssetSync(root, catalogue, List.of(unmanaged)).write();
    }

    @SneakyThrows
    private static String read(Path root, String path) {
        return Files.readString(root.resolve(path));
    }

    @Test
    void writesWhatIsMissing() {
        Path root = new GitFixture("sync-missing").write("README.md", "content\n").root();
        assertEquals(List.of(".editorconfig", ".gitignore"), this.sync(root), "both were absent");
        assertEquals(CANONICAL, read(root, ".editorconfig"), "and both hold what the harness ships");
        assertEquals(SEEDED, read(root, ".gitignore"), "and both hold what the harness ships");
    }

    @Test
    void rewritesAPinnedFileThatDrifted() {
        Path root = new GitFixture("sync-drifted")
            .write(".editorconfig", "root = false\n")
            .write(".gitignore", SEEDED)
            .root();
        assertEquals(List.of(".editorconfig"), this.sync(root), "only the pinned file needed writing");
        assertEquals(CANONICAL, read(root, ".editorconfig"), "and it now holds what the harness ships");
    }

    @Test
    void leavesASeedTheProjectHasMadeItsOwn() {
        Path root = new GitFixture("sync-seeded")
            .write(".editorconfig", CANONICAL)
            .write(".gitignore", "target/\nnode_modules/\n")
            .root();
        assertEquals(List.of(), this.sync(root), "there was nothing to write");
        assertEquals(
            "target/\nnode_modules/\n", read(root, ".gitignore"),
            "a seed's body belongs to the project the moment it exists"
        );
    }

    @Test
    void deletesNothingItForbids() {
        Path root = new GitFixture("sync-forbidden")
            .write(".editorconfig", CANONICAL)
            .write(".gitignore", SEEDED)
            .write("rewrite.yml", "a file somebody put here\n")
            .root();
        assertEquals(List.of(), this.sync(root), "a forbidden file is reported by the check, not removed here");
        assertTrue(
            Files.exists(root.resolve("rewrite.yml")),
            "a build tool deleting a developer's file on their behalf is worse than the problem it fixes"
        );
    }

    @Test
    void skipsWhatTheProjectHasTakenOver() {
        Path root = new GitFixture("sync-optout").write(".editorconfig", "root = false\n").root();
        assertEquals(List.of(".gitignore"), this.sync(root, ".editorconfig"), "the opted-out file was left alone");
        assertEquals("root = false\n", read(root, ".editorconfig"), "with the content the project chose");
    }

    @Test
    @SneakyThrows
    void refusesToWriteThroughASymbolicLink() {
        Path root = new GitFixture("sync-symlink").root();
        Path outside = this.shipped.resolve("outside");
        Files.writeString(outside, "must stay untouched\n");
        Files.createSymbolicLink(root.resolve(".editorconfig"), outside);
        assertThrows(
            IllegalStateException.class, () -> this.sync(root),
            "asset repair must never follow a project path into a file outside the repository"
        );
        assertEquals("must stay untouched\n", Files.readString(outside), "the external target was not rewritten");
    }
}
