package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies the word budgets of the Markdown an agent loads whole.
 */
class MarkdownBudgetsTest {

    private static final String BUDGETS = "word budget";
    private static final int SKILL_BUDGET = 4000;
    private static final int INSTRUCTION_BUDGET = 2000;

    @Test
    void namesASkillOverItsBudgetWithoutCountingItsFrontmatter() {
        String frontmatter = "---\nname: big\ndescription: Used when many words are wanted.\n---\n\n";
        Path root = new GitFixture("markdown-skill-budget")
            .write("over/SKILL.md", frontmatter + MarkdownCheckTest.words("over", SKILL_BUDGET + 1))
            .write("within/SKILL.md", frontmatter + MarkdownCheckTest.words("within", SKILL_BUDGET))
            .write("within/reference.md", MarkdownCheckTest.words("reference", SKILL_BUDGET * 2))
            .root();
        assertEquals(
            List.of("over/SKILL.md: the skill holds 4001 words, over its budget of 4000"), MarkdownCheckTest.offences(
                root, BUDGETS
            ),
            "the frontmatter is not counted, and a reference file carries no budget"
        );
    }

    @Test
    void countsOnlyTheProjectPartOfAnInstructionFile() {
        Path root = new GitFixture("markdown-instruction-budget")
            .write("over/AGENTS.md", instructions("over", INSTRUCTION_BUDGET + 1))
            .write("within/AGENTS.md", instructions("within", INSTRUCTION_BUDGET))
            .root();
        assertEquals(
            List.of("over/AGENTS.md: the project-owned instruction text holds 2001 words, over its budget of 2000"),
            MarkdownCheckTest.offences(root, BUDGETS),
            "the managed block does not count against the project's budget"
        );
    }

    private static String instructions(String word, int count) {
        return MarkdownCheckTest.MANAGED + "\n" + MarkdownCheckTest.words(word, count);
    }
}
