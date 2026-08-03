package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies the fixed {@code AGENTS.md} and {@code CLAUDE.md} contract.
 */
class EntryFileCheckTest {

    private static final String INSTRUCTION_BODY = "# Rules\n\nEvery rule lives here.\n";

    @Test
    void passesWhenBothFixedFilesSatisfyTheirContracts() {
        Path root = new GitFixture("entry-clean")
            .write(EntryFileRules.INSTRUCTIONS, INSTRUCTION_BODY)
            .write(EntryFileRules.CLAUDE, EntryFileRules.CLAUDE_CONTENT)
            .root();
        assertTrue(
            Verdicts.clean(new EntryFileCheck(root).findings()),
            "the root file has instructions and the Claude entry is the exact fixed reference"
        );
    }

    @Test
    void reportsAMissingInstructionFile() {
        Path root = new GitFixture("entry-no-instructions")
            .write(EntryFileRules.CLAUDE, EntryFileRules.CLAUDE_CONTENT)
            .root();
        assertEquals(
            List.of(EntryFileRules.INSTRUCTIONS),
            Verdicts.offences(new EntryFileCheck(root).findings(), "AGENTS.md file is missing"),
            "the root instruction file is mandatory"
        );
    }

    @Test
    void reportsAnInstructionFileWithNoInstructions() {
        Path root = new GitFixture("entry-empty-instructions")
            .write(EntryFileRules.INSTRUCTIONS, " \n")
            .write(EntryFileRules.CLAUDE, EntryFileRules.CLAUDE_CONTENT)
            .root();
        assertEquals(
            List.of(EntryFileRules.INSTRUCTIONS),
            Verdicts.offences(new EntryFileCheck(root).findings(), "contains no instructions"),
            "a file with no prose does not satisfy the mandatory instruction contract"
        );
    }

    @Test
    void reportsAMissingClaudeEntryFile() {
        Path root = new GitFixture("entry-no-claude")
            .write(EntryFileRules.INSTRUCTIONS, INSTRUCTION_BODY)
            .root();
        assertEquals(
            List.of(EntryFileRules.CLAUDE),
            Verdicts.offences(new EntryFileCheck(root).findings(), "CLAUDE.md file is missing"),
            "Claude must have its fixed entry point"
        );
    }

    @Test
    void reportsAnyContentBeyondTheExactClaudeReference() {
        Path root = new GitFixture("entry-claude-drift")
            .write(EntryFileRules.INSTRUCTIONS, INSTRUCTION_BODY)
            .write(EntryFileRules.CLAUDE, "@AGENTS.md\nRun Maven first.\n")
            .root();
        assertEquals(
            List.of(EntryFileRules.CLAUDE),
            Verdicts.offences(new EntryFileCheck(root).findings(), "must contain exactly"),
            "the entry file has no project-owned instructions of its own"
        );
    }
}
