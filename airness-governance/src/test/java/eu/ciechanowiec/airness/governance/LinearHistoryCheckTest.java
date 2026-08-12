package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The linear-history check reads the shape of the history rather than the text of its messages. A commit
 * git recorded with two parents is a merge whatever its header says, and a header that opens with the word
 * merge is not one.
 */
class LinearHistoryCheckTest {

    private static final String FILE = "README.md";

    @Test
    void passesOverAHistoryThatOnlyEverMovedForward() {
        Path root = new GitFixture("linear-history-clean")
            .write(FILE, "First.\n").commit("feat(core): add the first fixture file")
            .write(FILE, "Second.\n").commit("docs(core): describe the fixture file")
            .root();
        LinearHistoryCheck check = new LinearHistoryCheck(root);
        assertEquals(2, check.scanned(), "both commits were read");
        assertTrue(Verdicts.clean(check.findings()), "and neither of them joins a second parent");
    }

    @Test
    void reportsAMergeCommitAndNamesIt() {
        Path root = new GitFixture("linear-history-merged")
            .write(FILE, "Base.\n").commit("feat(core): add the base fixture file")
            .mergeASideBranch()
            .root();
        List<String> offences = Verdicts.offences(new LinearHistoryCheck(root).findings(), "Merge commits");
        String head = GitPlumbing.run(root, List.of("rev-parse", "HEAD")).strip();
        assertEquals(
            List.of(head + ": a merge commit, where a rebase or a cherry-pick would record one parent"),
            offences,
            "only the merge is reported, and it is named, because a published merge outlives every rewrite"
        );
    }

    @Test
    void readsAMergeLikeSubjectAsAnOrdinaryCommit() {
        Path root = new GitFixture("linear-history-merge-like")
            .write(FILE, "First.\n").commit("chore(core): merge the fixture sections")
            .root();
        LinearHistoryCheck check = new LinearHistoryCheck(root);
        assertEquals(1, check.scanned(), "the one commit was read");
        assertTrue(Verdicts.clean(check.findings()), "and a subject that says merge is not a second parent");
    }
}
