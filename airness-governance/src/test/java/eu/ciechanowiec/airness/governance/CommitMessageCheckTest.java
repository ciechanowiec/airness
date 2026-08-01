package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The pending-message check reads the message being written and sizes it against what is actually
 * staged, so the body requirement follows the change rather than a guess about it.
 */
class CommitMessageCheckTest {

    private static final String FILE = "README.md";
    private static final String MESSAGE = "commit-message.txt";
    private static final String BODY = "A line of content.\n";

    private static GitFixture staged(String name, String message) {
        return new GitFixture(name)
            .write(FILE, "First.\n").commit("feat(core): add the first fixture file")
            .write(FILE, BODY).stage()
            .write(MESSAGE, message);
    }

    @Test
    void passesAWellFormedMessageOverATrivialChange() {
        Path root = staged("message-clean", "feat(core): rewrite the fixture content\n").root();
        assertTrue(
            Verdicts.clean(new CommitMessageCheck(root, root.resolve(MESSAGE)).findings()),
            "one file and one changed line ask for no body"
        );
    }

    @Test
    void reportsAHeaderThatIsNotConventional() {
        Path root = staged("message-broken", "rewrote some things\n").root();
        assertFalse(
            Verdicts.offences(new CommitMessageCheck(root, root.resolve(MESSAGE)).findings(), "violates policy")
                .isEmpty(),
            "catching this before the message is written is the point, since afterwards only a rewrite is left"
        );
    }

    @Test
    void refusesToReportOnAMessageFileItCouldNotOpen() {
        Path root = staged("message-absent", "feat(core): rewrite the fixture content\n").root();
        Path absent = root.resolve("no-such-message.txt");
        IllegalStateException thrown = assertThrows(
            IllegalStateException.class, () -> new CommitMessageCheck(root, absent)
        );
        assertTrue(thrown.getMessage().contains("no-such-message.txt"), "and says which file it could not read");
    }

    @Test
    void sizesTheRequirementAgainstWhatIsStagedRatherThanTheWholeTree() {
        Path root = staged("message-sized", "feat(core): rewrite the fixture content\n")
            .write("unstaged.txt", BODY)
            .root();
        assertEquals(
            List.of(),
            Verdicts.offences(new CommitMessageCheck(root, root.resolve(MESSAGE)).findings(), "violates policy"),
            "a file present but unstaged is not part of the change the message describes"
        );
    }
}
