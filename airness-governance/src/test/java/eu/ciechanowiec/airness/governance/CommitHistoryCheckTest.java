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
}
