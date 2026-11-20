package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * The size of the Markdown an agent loads whole.
 *
 * <p>An agent reads every {@code AGENTS.md} on its way to a file and a skill's {@code SKILL.md} each time it
 * invokes the skill, so each word there is paid on every run. Words are counted rather than lines, since a
 * paragraph is one line of source and a line count would measure nothing. A skill's frontmatter is left out,
 * being the part the runtime reads anyway, and so are the blocks Airness manages in an instruction file, which
 * the project does not write. The reference files a skill links to are loaded on demand and carry no budget.
 */
@UtilityClass
final class MarkdownBudgets {

    private static final int SKILL_WORDS = 4000;
    private static final int INSTRUCTION_WORDS = 2000;
    private static final Pattern FRONTMATTER = Pattern.compile("\\A---\\n[\\s\\S]*?\\n---(?:\\n|\\z)");
    private static final Pattern WORD = Pattern.compile("\\S+");

    /**
     * The document's excess over its budget, if it has a budget and exceeds it.
     *
     * @param document a parsed document
     * @return one offence, or none
     */
    static List<String> excess(MarkdownDocument document) {
        String text = document.text();
        return switch (document.path().substring(document.path().lastIndexOf('/') + 1)) {
            case SkillManifestCheck.MANIFEST -> over(
                document, words(FRONTMATTER.matcher(text).replaceFirst("")), SKILL_WORDS, "skill"
            );
            case EntryFileRules.INSTRUCTIONS -> over(
                document, words(ManagedSections.projectPart(text).orElse(text)), INSTRUCTION_WORDS,
                "project-owned instruction text"
            );
            default -> List.of();
        };
    }

    private static List<String> over(MarkdownDocument document, long words, int budget, String part) {
        return words > budget
            ? List.of(
                "%s: the %s holds %d words, over its budget of %d".formatted(document.path(), part, words, budget)
            )
            : List.of();
    }

    private static long words(CharSequence text) {
        return WORD.matcher(text).results().count();
    }
}
