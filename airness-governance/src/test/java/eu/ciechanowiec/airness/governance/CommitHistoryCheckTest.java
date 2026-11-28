package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The commit-history check applies the message policy to every commit it can reach, counts what it read,
 * and names the commit each violation belongs to.
 */
class CommitHistoryCheckTest {

    private static final String FILE = "README.md";

    @Test
    void passesOverAHistoryOfWellFormedMessages() {
        Path root = new GitFixture("history-clean")
            .write(FILE, "First.\n").commit("feat(core): add the first fixture file")
            .write(FILE, "Second.\n").commit("docs(core): describe the fixture file")
            .root();
        CommitHistoryCheck check = new CommitHistoryCheck(root);
        assertEquals(2, check.scanned(), "both commits were read");
        assertTrue(Verdicts.clean(check.findings()), "and both satisfy the policy");
    }

    @Test
    void reportsAMessageThatBreaksThePolicyAndNamesItsCommit() {
        Path root = new GitFixture("history-broken")
            .write(FILE, "First.\n").commit("feat(core): add the first fixture file")
            .write(FILE, "Second.\n").commit("wip")
            .root();
        List<String> offences = Verdicts.offences(new CommitHistoryCheck(root).findings(), "break the policy");
        assertFalse(offences.isEmpty(), "a bare junk word is not a conventional header");
        String head = GitPlumbing.run(root, List.of("rev-parse", "HEAD")).strip();
        assertTrue(
            offences.stream().allMatch(offence -> offence.startsWith(head)),
            "and every offence names the commit it belongs to, since a rewrite is the only other remedy: " + offences
        );
    }

    @Test
    void reportsTheWritingRulesOfAStoredCrlfBody() {
        GitFixture fixture = new GitFixture("history-crlf").write(FILE, "First.\n").stage();
        fixture.git(
            "commit", "--quiet", "--no-verify", "--cleanup=verbatim", "--message",
            "feat(core): add the first fixture file\n\nThe file is read\r\nat start; it stays.\r\n"
        );
        List<String> offences = Verdicts.offences(
            new CommitHistoryCheck(fixture.root()).findings(), "break the policy"
        );
        String head = GitPlumbing.run(fixture.root(), List.of("rev-parse", "HEAD")).strip();
        assertEquals(
            List.of(
                head + ": A semicolon outside a code span, where two sentences or a connecting word belong: "
                    + "at start; it stays.",
                head + ": A body paragraph runs onto a second source line, so join it into one: at start; it stays."
            ),
            offences,
            "the history check applies the writing rules to the message git stored, carriage returns and all"
        );
    }

    @Test
    void readsAMergeCommitLikeEveryOtherCommit() {
        Path root = new GitFixture("history-merge")
            .write(FILE, "Base.\n").commit("feat(core): add the base fixture file")
            .mergeASideBranch()
            .root();
        CommitHistoryCheck check = new CommitHistoryCheck(root);
        assertEquals(4, check.scanned(), "the base, both branch tips, and the merge itself were read");
        assertFalse(
            Verdicts.clean(check.findings()),
            "the header git writes for a merge is a header, and this check reads every one of them"
        );
    }
}
