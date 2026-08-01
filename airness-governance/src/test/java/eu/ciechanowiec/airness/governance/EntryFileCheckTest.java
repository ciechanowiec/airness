package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The entry-file check answers all four of its rules separately, and reports absence rather than
 * throwing on it. Asserting that a declared entry file is there is half of what the check is for, so an
 * absent one has to be a finding it can print alongside the others.
 */
class EntryFileCheckTest {

    private static final String INSTRUCTIONS = "AGENTS.md";
    private static final String ENTRY = "CLAUDE.md";
    private static final List<String> ENTRIES = List.of(ENTRY);
    private static final String INSTRUCTION_BODY = "# Rules\n\nEvery rule lives here.\n";

    @Test
    void passesWhenTheEntryFileHoldsNothingButTheReference() {
        Path root = new GitFixture("entry-clean")
            .write(INSTRUCTIONS, INSTRUCTION_BODY)
            .write(ENTRY, "@AGENTS.md\n")
            .root();
        assertTrue(
            Verdicts.clean(new EntryFileCheck(root, INSTRUCTIONS, ENTRIES).findings()),
            "a bare reference is the whole of what an entry file may say"
        );
    }

    @Test
    void reportsARuleStatedInTheEntryFile() {
        Path root = new GitFixture("entry-says-more")
            .write(INSTRUCTIONS, INSTRUCTION_BODY)
            .write(ENTRY, "@AGENTS.md\nAlways run the verification command.\n")
            .root();
        assertEquals(
            1, Verdicts.offences(new EntryFileCheck(root, INSTRUCTIONS, ENTRIES).findings(), "states what only").size(),
            "a second home for the rules is what this rule exists to catch"
        );
    }

    @Test
    void reportsAnEntryFileThatPointsNowhere() {
        Path root = new GitFixture("entry-silent")
            .write(INSTRUCTIONS, INSTRUCTION_BODY)
            .write(ENTRY, "See the repository documentation.\n")
            .root();
        List<Findings> findings = new EntryFileCheck(root, INSTRUCTIONS, ENTRIES).findings();
        assertEquals(
            List.of(ENTRY), Verdicts.offences(findings, "does not point"),
            "an entry file naming no instruction file leaves the tool that opens it with nothing"
        );
    }

    @Test
    void reportsADeclaredEntryFileThatIsNotThere() {
        Path root = new GitFixture("entry-absent").write(INSTRUCTIONS, INSTRUCTION_BODY).root();
        assertEquals(
            List.of(ENTRY),
            Verdicts.offences(new EntryFileCheck(root, INSTRUCTIONS, ENTRIES).findings(), "entry file is missing"),
            "a declared name that no file answers is a finding rather than a silently tolerated absence"
        );
    }

    @Test
    void reportsTheMissingInstructionFileEveryEntryFilePointsAt() {
        Path root = new GitFixture("entry-no-instructions").write(ENTRY, "@AGENTS.md\n").root();
        assertEquals(
            List.of(INSTRUCTIONS),
            Verdicts.offences(new EntryFileCheck(root, INSTRUCTIONS, ENTRIES).findings(), "instruction file every"),
            "an entry file pointing at nothing is worse than one that says too much"
        );
    }
}
