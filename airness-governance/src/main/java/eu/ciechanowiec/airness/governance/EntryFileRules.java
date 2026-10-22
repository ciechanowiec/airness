package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * States the fixed agent-instruction file contract. {@code AGENTS.md} combines a harness-owned leading
 * section with project-owned prose and is the only instruction file, so no file Claude Code reads in its
 * place may exist anywhere in the tree.
 */
@UtilityClass
final class EntryFileRules {

    static final String INSTRUCTIONS = "AGENTS.md";
    // Compared in lower case: on a case-insensitive file system claude.md is the same file to the tool.
    private static final Set<String> FORBIDDEN = Set.of("claude.md", "claude.local.md");

    static boolean hasInstructions(CharSequence content) {
        return content.codePoints().anyMatch(Character::isLetterOrDigit);
    }

    static boolean isForbidden(Path file) {
        return FORBIDDEN.contains(file.getFileName().toString().toLowerCase(Locale.ROOT));
    }
}
