package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * The tree fingerprint covers committable content and ignores build output.
 */
class TreeFingerprintTest {

    @Test
    void changesWithTrackedAndUntrackedContent() {
        GitFixture fixture = new GitFixture("fingerprint-content")
            .write("tracked.txt", "first\n").commit("test(core): add the tracked fixture file");
        Path root = fixture.root();
        String initial = TreeFingerprint.from(root);
        fixture.write("tracked.txt", "second\n");
        String modified = TreeFingerprint.from(root);
        fixture.write("untracked.txt", "new\n");
        assertNotEquals(initial, modified, "editing a tracked file changes the build's input tree");
        assertNotEquals(modified, TreeFingerprint.from(root), "an untracked committable file also changes it");
    }

    @Test
    void ignoresFilesExcludedByGit() {
        GitFixture fixture = new GitFixture("fingerprint-ignored")
            .write(".gitignore", "target/\n")
            .write("tracked.txt", "content\n")
            .commit("test(core): add the ignored-output fixture");
        Path root = fixture.root();
        String initial = TreeFingerprint.from(root);
        fixture.write("target/generated.txt", "build output\n");
        assertEquals(initial, TreeFingerprint.from(root), "build output is outside the committable tree");
    }

    @Test
    @SneakyThrows
    void fingerprintsTheTargetOfASymbolicLink() {
        Path root = new GitFixture("fingerprint-symlink").root();
        Path link = root.resolve("link");
        Files.createSymbolicLink(link, Path.of("first"));
        String initial = TreeFingerprint.from(root);
        Files.delete(link);
        Files.createSymbolicLink(link, Path.of("second"));
        assertNotEquals(initial, TreeFingerprint.from(root), "changing only a link target is still a tree change");
    }

    @Test
    @SneakyThrows
    void distinguishesASymbolicLinkFromARegularFileWithTheSameBytes() {
        Path root = new GitFixture("fingerprint-entry-type").root();
        Path entry = root.resolve("entry");
        Files.createSymbolicLink(entry, Path.of("payload"));
        String linked = TreeFingerprint.from(root);
        Files.delete(entry);
        Files.writeString(entry, "payload");
        assertNotEquals(linked, TreeFingerprint.from(root), "a file and a link are different repository entries");
    }
}
