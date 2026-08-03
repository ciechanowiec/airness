package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Verifies the content rules for the two fixed agent files. */
class EntryFileRulesTest {

    @Test
    void acceptsNonBlankInstructions() {
        assertTrue(EntryFileRules.hasInstructions("# Build rules\n"));
    }

    @Test
    void rejectsAnInstructionFileWithNoInstructions() {
        assertFalse(EntryFileRules.hasInstructions("# \n\t"));
    }

    @Test
    void acceptsTheExactClaudeEntry() {
        assertTrue(EntryFileRules.isClaudeEntry("@AGENTS.md\n"));
    }

    @Test
    void rejectsExtraClaudeContent() {
        assertFalse(EntryFileRules.isClaudeEntry("@AGENTS.md\nRun Maven.\n"));
    }

    @Test
    void rejectsAClaudeEntryWithoutItsRequiredFinalNewline() {
        assertFalse(EntryFileRules.isClaudeEntry("@AGENTS.md"));
    }
}
