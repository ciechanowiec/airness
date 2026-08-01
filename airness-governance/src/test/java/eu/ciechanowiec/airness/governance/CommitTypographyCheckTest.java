package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The commit-typography check reads the messages the tree scan cannot reach, and names the commit each
 * banned code point sits in.
 *
 * <p>The banned glyph is built from its code point rather than typed, because this file is itself
 * scanned by {@link TypographyRules} and would otherwise be the first thing the rule found.
 */
class CommitTypographyCheckTest {

    private static final int EM_DASH = 0x2014;
    private static final String FILE = "README.md";

    @Test
    void passesOverMessagesThatUsePlainAscii() {
        Path root = new GitFixture("commit-typography-clean")
            .write(FILE, "First.\n").commit("feat(core): add the first fixture file")
            .root();
        CommitTypographyCheck check = new CommitTypographyCheck(root);
        assertEquals(1, check.scanned(), "the one commit was read");
        assertTrue(Verdicts.clean(check.findings()), "and its message is plain ASCII");
    }

    @Test
    void reportsABannedCodePointAndNamesItsCommit() {
        String offending = "feat(core): add the fixture file " + Character.toString(EM_DASH) + " the first one";
        Path root = new GitFixture("commit-typography-broken")
            .write(FILE, "First.\n").commit(offending)
            .root();
        List<String> offences = Verdicts.offences(new CommitTypographyCheck(root).findings(), "commit messages");
        String head = GitPlumbing.run(root, List.of("rev-parse", "HEAD")).strip();
        assertEquals(
            List.of(head + ": U+2014"), offences,
            "a commit message is the one piece of prose a repository can never correct in place"
        );
    }
}
