package eu.ciechanowiec.airness.governance;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * States the fixed agent-instruction file contract. {@code AGENTS.md} combines a harness-owned leading
 * section with project-owned prose and is the only instruction file, so no file Claude Code reads in its
 * place may exist anywhere in the tree.
 *
 * <p>The other agent tools each read one file at the root, which may only point at {@code AGENTS.md} and is
 * checked as a reference file with the managed files. They also read a directory of rule files, or a file of
 * their own name in any directory, and neither form can be a reference: a rule file is matched to the paths it
 * covers, and a nested file adds to the root one. Both forms are refused wherever they lie.
 */
@UtilityClass
final class EntryFileRules {

    static final String INSTRUCTIONS = "AGENTS.md";
    // Compared in lower case: on a case-insensitive file system claude.md is the same file to the tool.
    private static final Set<String> FORBIDDEN = Set.of("claude.md", "claude.local.md");
    // Matched against the path below the root, in lower case and with forward slashes.
    private static final List<Pattern> TOOL_INSTRUCTIONS = List.of(
        Pattern.compile("(?:.+/)?\\.clinerules/.+"),
        Pattern.compile("(?:.+/)?\\.cursor/rules/.+"),
        Pattern.compile("(?:.+/)?\\.windsurf/rules/.+"),
        Pattern.compile("(?:.+/)?\\.github/instructions/(?:.+/)?[^/]+\\.instructions\\.md"),
        Pattern.compile(".+/gemini\\.md")
    );

    static boolean hasInstructions(CharSequence content) {
        return content.codePoints().anyMatch(Character::isLetterOrDigit);
    }

    static boolean isForbidden(Path file) {
        return FORBIDDEN.contains(file.getFileName().toString().toLowerCase(Locale.ROOT));
    }

    static boolean isToolInstruction(Path relative) {
        String path = relative.toString().replace(File.separatorChar, '/').toLowerCase(Locale.ROOT);
        return TOOL_INSTRUCTIONS.stream().anyMatch(pattern -> pattern.matcher(path).matches());
    }
}
