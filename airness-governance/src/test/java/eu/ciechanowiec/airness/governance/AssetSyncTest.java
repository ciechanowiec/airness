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

    private static final String EDITORCONFIG = ".editorconfig";
    private static final String GITIGNORE = ".gitignore";
    private static final String GUIDE = ".airness/agent-guide.md";

    private static final String MANIFEST = """
        .editorconfig\tPINNED
        .airness/agent-guide.md\tPINNED
        .gitignore\tSEED
        rewrite.yml\tFORBIDDEN
        """;

    private static final String CANONICAL = "root = true\n";
    private static final String SEEDED = "target/\n";

    @TempDir
    private Path shipped;

    private List<String> sync(Path root, String... unmanaged) {
        AssetCatalogue catalogue = new AssetFixture(this.shipped, MANIFEST)
            .ship(EDITORCONFIG, CANONICAL)
            .ship(GUIDE, "guide\n")
            .ship(GITIGNORE, SEEDED)
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
        assertEquals(
            List.of(EDITORCONFIG, GUIDE, GITIGNORE), this.sync(root),
            "all managed files were absent"
        );
        assertEquals(CANONICAL, read(root, EDITORCONFIG), "and both hold what the harness ships");
        assertEquals(SEEDED, read(root, GITIGNORE), "and both hold what the harness ships");
    }

    @Test
    void rewritesAPinnedFileThatDrifted() {
        Path root = new GitFixture("sync-drifted")
            .write(EDITORCONFIG, "root = false\n")
            .write(GITIGNORE, SEEDED)
            .root();
        assertEquals(
            List.of(EDITORCONFIG, GUIDE), this.sync(root),
            "the pinned file drifted and the pinned guide was absent"
        );
        assertEquals(CANONICAL, read(root, EDITORCONFIG), "and it now holds what the harness ships");
    }

    @Test
    void leavesASeedTheProjectHasMadeItsOwn() {
        Path root = new GitFixture("sync-seeded")
            .write(EDITORCONFIG, CANONICAL)
            .write(GITIGNORE, "target/\nnode_modules/\n")
            .root();
        assertEquals(List.of(GUIDE), this.sync(root), "only the pinned guide was absent");
        assertEquals(
            "target/\nnode_modules/\n", read(root, GITIGNORE),
            "a seed's body belongs to the project the moment it exists"
        );
    }

    @Test
    void deletesNothingItForbids() {
        Path root = new GitFixture("sync-forbidden")
            .write(EDITORCONFIG, CANONICAL)
            .write(GITIGNORE, SEEDED)
            .write("rewrite.yml", "a file somebody put here\n")
            .root();
        assertEquals(
            List.of(GUIDE), this.sync(root),
            "the guide was written and the forbidden file was not removed"
        );
        assertTrue(
            Files.exists(root.resolve("rewrite.yml")),
            "a build tool deleting a developer's file on their behalf is worse than the problem it fixes"
        );
    }

    @Test
    void skipsWhatTheProjectHasTakenOver() {
        Path root = new GitFixture("sync-optout").write(EDITORCONFIG, "root = false\n").root();
        assertEquals(
            List.of(GUIDE, GITIGNORE), this.sync(root, EDITORCONFIG),
            "the opted-out file was left alone"
        );
        assertEquals("root = false\n", read(root, EDITORCONFIG), "with the content the project chose");
    }

    @Test
    @SneakyThrows
    void refusesToWriteThroughASymbolicLink() {
        Path root = new GitFixture("sync-symlink").root();
        Path outside = this.shipped.resolve("outside");
        Files.writeString(outside, "must stay untouched\n");
        Files.createSymbolicLink(root.resolve(EDITORCONFIG), outside);
        assertThrows(
            IllegalStateException.class, () -> this.sync(root),
            "asset repair must never follow a project path into a file outside the repository"
        );
        assertEquals("must stay untouched\n", Files.readString(outside), "the external target was not rewritten");
    }

    @Test
    void skipsAnOptedOutAgentGuide() {
        Path root = new GitFixture("sync-guide-optout")
            .write(EDITORCONFIG, CANONICAL)
            .write(GUIDE, "changed\n")
            .write(GITIGNORE, SEEDED)
            .root();
        assertEquals(List.of(), this.sync(root, GUIDE), "the opted-out guide was left alone");
        assertEquals("changed\n", read(root, GUIDE), "the project-owned content was preserved");
    }
}
