package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The history is read by one stream, and what it reads is what a command per commit would have read: the
 * same hashes in the same order, the same messages, the same change statistics and the same merges.
 */
class CommitLogTest {

    private static final String FILE = "README.md";
    private static final String NUL = Character.toString(0);

    @Test
    void readsEachCommitAsACommandPerCommitWouldReadIt() {
        Path root = new GitFixture("commit-log-equivalent")
            .write(FILE, "First.\n").commit("feat(core): add the first fixture file")
            .write(FILE, "Second.\n").write("notes.txt", "One.\nTwo.\n")
            .commit("docs(core): describe the fixture files\n\nThe notes record why the file exists.")
            .mergeASideBranch()
            .root();
        List<Commit> expected = GitPlumbing.run(root, List.of("rev-list", "--parents", "HEAD")).lines()
            .map(line -> expectedCommit(root, line))
            .toList();
        assertEquals(expected, CommitLog.commits(root), "one stream reads the history a command per commit read");
    }

    @Test
    void keepsAMessageThatQuotesNumstatRowsApartFromTheStatistics() {
        String body = "The rows below are quoted, not changed:\n\n12\t3\tsrc/Main.java\n40\t0\tsrc/Other.java";
        Path root = new GitFixture("commit-log-quoted-rows")
            .write(FILE, "Only.\n").commit("docs(core): quote a numstat row in the body\n\n" + body)
            .root();
        Commit commit = CommitLog.commits(root).getFirst();
        assertEquals(body, commit.message().body(), "the quoted rows stay in the message");
        assertEquals(new DiffStat(1, 1), commit.stat(), "and the statistics count only the file the commit changed");
    }

    @Test
    void readsNoCommitFromAnEmptyOutput() {
        assertEquals(List.of(), CommitLog.parse(""), "an output without a record holds no commit");
    }

    @Test
    void readsARecordWithoutAMessageLineAsACommitWithAnEmptyMessage() {
        Commit expected = new Commit("abc", CommitMessages.parse(""), DiffStats.parse(""), false);
        assertEquals(
            List.of(expected), CommitLog.parse(NUL + "abc def" + NUL + NUL),
            "a record holding only the hashes still yields its commit"
        );
    }

    private static Commit expectedCommit(Path root, String line) {
        String[] hashes = line.strip().split("\\s+", -1);
        String sha = hashes[0];
        String message = GitPlumbing.run(root, List.of("show", "--no-patch", "--format=%B", sha));
        String numstat = GitPlumbing.run(root, List.of("show", "--numstat", "--format=", sha));
        return new Commit(sha, CommitMessages.parse(message), DiffStats.parse(numstat), hashes.length > 2);
    }
}
