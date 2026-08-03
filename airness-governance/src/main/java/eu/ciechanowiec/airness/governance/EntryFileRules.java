package eu.ciechanowiec.airness.governance;

import lombok.experimental.UtilityClass;

/**
 * States the fixed agent-instruction file contract. {@code AGENTS.md} is project-owned prose, while
 * {@code CLAUDE.md} is a tool entry point whose complete body is fixed by the harness.
 */
@UtilityClass
final class EntryFileRules {

    static final String INSTRUCTIONS = "AGENTS.md";
    static final String CLAUDE = "CLAUDE.md";
    static final String CLAUDE_CONTENT = "@AGENTS.md\n";

    static boolean hasInstructions(String content) {
        return content.codePoints().anyMatch(Character::isLetterOrDigit);
    }

    static boolean isClaudeEntry(String content) {
        return CLAUDE_CONTENT.equals(content);
    }
}
