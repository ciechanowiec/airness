package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The repository reader answers from git rather than from the current directory, tells an unborn history
 * from a truncated one, and reads an absent path as no text rather than as a failure.
 */
class RepositoryTest {

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

    @Test
    void readsAnAbsentPathAsNoTextRatherThanFailing() {
        Path root = new GitFixture("repository-absent").write("README.md", "content\n").root();
        assertTrue(
            Repository.readText(root.resolve("nothing-here.md")).isEmpty(),
            "a check that asserts a file is present has to be able to report the absence rather than crash on it"
        );
    }
}
