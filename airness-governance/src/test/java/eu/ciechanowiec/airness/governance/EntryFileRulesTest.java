package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Verifies the content and naming rules for agent instruction files.
 */
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
    void forbidsAClaudeEntryFileInAnyDirectory() {
        assertTrue(EntryFileRules.isForbidden(Path.of("module", "CLAUDE.md")));
    }

    @Test
    void forbidsALocalClaudeFile() {
        assertTrue(EntryFileRules.isForbidden(Path.of("CLAUDE.local.md")));
    }

    @Test
    void forbidsAClaudeEntryFileWhateverItsCase() {
        assertTrue(EntryFileRules.isForbidden(Path.of("claude.md")));
    }

    @Test
    void allowsAFileThatOnlyMentionsClaude() {
        assertFalse(EntryFileRules.isForbidden(Path.of("docs", "CLAUDE-notes.md")));
    }
}
