package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * The repository reader answers from git rather than from the current directory, tells an unborn history
 * from a truncated one, and reads an absent path as no text rather than as a failure.
 */
class RepositoryTest {

    private static final byte MALFORMED_UTF8_LEAD = (byte) 0xC3;
    private static final byte ASCII_LEFT_PARENTHESIS = 0x28;

    @Test
    void findsTheWorkingTreeRootFromADirectoryInsideIt() {
        Path root = new GitFixture("repository-root").write("nested/deep/file.txt", "content\n").root();
        assertEquals(
            root, Repository.rootFrom(root.resolve("nested/deep")),
            "asking git makes the answer the same from every module, and the module directory is the wrong one"
        );
    }

    @Test
    void readsARepositoryWithNoCommitsAsUnborn() {
        Path root = new GitFixture("repository-unborn").write("README.md", "content\n").root();
        assertFalse(Repository.hasCommits(root), "a first commit that is not yet written is a legitimate state");
        assertFalse(Repository.isShallow(root), "and is not the same thing as a clone whose commits were not fetched");
    }

    @Test
    void readsARepositoryWithHistoryAsBorn() {
        Path root = new GitFixture("repository-born")
            .write("README.md", "content\n").commit("feat(core): add the first fixture file")
            .root();
        assertTrue(Repository.hasCommits(root), "one commit is history enough for the checks that read it");
    }

    // The guard and the readers have to name the same ref. A fresh orphan branch beside an existing one is
    // a repository that has commits and an unborn HEAD at the same time, so a guard counting every ref
    // answers yes and then sends the history checks on to read a HEAD that resolves to nothing.
    @Test
    void readsAnUnbornBranchAsUnbornThoughAnotherBranchCarriesCommits() {
        GitFixture fixture = new GitFixture("repository-orphan")
            .write("README.md", "content\n").commit("feat(core): add the first fixture file");
        fixture.git("checkout", "--orphan", "unborn");
        assertFalse(
            Repository.hasCommits(fixture.root()),
            "HEAD is what every check acting on this answer goes on to read"
        );
    }

    @Test
    void readsAnAbsentPathAsNoTextRatherThanFailing() {
        Path root = new GitFixture("repository-absent").write("README.md", "content\n").root();
        assertTrue(
            Repository.readText(root.resolve("nothing-here.md")).isEmpty(),
            "a check that asserts a file is present has to be able to report the absence rather than crash on it"
        );
    }

    @Test
    @SneakyThrows
    void readsSymbolicLinksAsTheirDeclaredTarget() {
        Path root = new GitFixture("repository-link").root();
        Path link = root.resolve("linked");
        Files.createSymbolicLink(link, Path.of("target.txt"));
        assertEquals("target.txt", Repository.readText(link).orElseThrow());
    }

    @Test
    @SneakyThrows
    void excludesBinaryAndMalformedUtf8FromText() {
        Path root = new GitFixture("repository-bytes").root();
        Path binary = Files.write(root.resolve("binary"), new byte[]{1, 0, 2});
        Path malformed = Files.write(
            root.resolve("malformed"), new byte[]{MALFORMED_UTF8_LEAD, ASCII_LEFT_PARENTHESIS}
        );
        assertTrue(Repository.readText(binary).isEmpty());
        assertTrue(Repository.readText(malformed).isEmpty());
    }

    @Test
    @SneakyThrows
    void includesACommittableSymbolicLinkButNotADirectory() {
        Path root = new GitFixture("repository-entries").write("ordinary.txt", "text\n").root();
        Files.createSymbolicLink(root.resolve("linked"), Path.of("ordinary.txt"));
        Files.createDirectory(root.resolve("directory"));
        assertTrue(Repository.trackedFiles(root).contains(root.resolve("linked")));
        assertFalse(Repository.trackedFiles(root).contains(root.resolve("directory")));
    }
}
