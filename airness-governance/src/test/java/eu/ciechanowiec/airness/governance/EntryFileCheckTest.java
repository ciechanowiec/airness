package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Verifies the fixed {@code AGENTS.md} contract and the {@code CLAUDE.md} ban.
 */
class EntryFileCheckTest {

    private static final String INSTRUCTION_BODY = "# Rules\n\nEvery rule lives here.\n";
    private static final String OWN_RULE = "Use tabs.\n";
    private static final String AIRNESS = """
        <!-- BEGIN AIRNESS MANAGED INSTRUCTIONS -->
        Airness rules.
        <!-- END AIRNESS MANAGED INSTRUCTIONS -->
        """;

    private static EntryFileCheck check(Path root) {
        return new EntryFileCheck(root);
    }

    @Test
    void passesWhenTheInstructionFileSatisfiesItsContract() {
        Path root = new GitFixture("entry-clean")
            .write(EntryFileRules.INSTRUCTIONS, AIRNESS + INSTRUCTION_BODY)
            .root();
        assertTrue(
            Verdicts.clean(check(root).findings()),
            "the root file has instructions and no Claude entry file competes with it"
        );
    }

    @Test
    void reportsAMissingInstructionFile() {
        Path root = new GitFixture("entry-no-instructions")
            .root();
        assertEquals(
            List.of(EntryFileRules.INSTRUCTIONS),
            Verdicts.offences(check(root).findings(), "AGENTS.md file is missing"),
            "the root instruction file is mandatory"
        );
    }

    @Test
    void reportsAnInstructionFileWithNoInstructions() {
        Path root = new GitFixture("entry-empty-instructions")
            .write(EntryFileRules.INSTRUCTIONS, " \n")
            .root();
        assertEquals(
            List.of(EntryFileRules.INSTRUCTIONS),
            Verdicts.offences(check(root).findings(), "contains no instructions"),
            "a file with no prose does not satisfy the mandatory instruction contract"
        );
    }

    @Test
    void reportsEveryClaudeEntryFileInTheTree() {
        Path root = new GitFixture("entry-claude-forbidden")
            .write(EntryFileRules.INSTRUCTIONS, AIRNESS + INSTRUCTION_BODY)
            .write("CLAUDE.md", "@AGENTS.md\n")
            .write("module/CLAUDE.md", "Run Maven first.\n")
            .root();
        assertEquals(
            Set.of("CLAUDE.md", Path.of("module", "CLAUDE.md").toString()),
            Set.copyOf(Verdicts.offences(check(root).findings(), "CLAUDE.md files are forbidden")),
            "Claude Code reads AGENTS.md, so even a bare reference to it is a second instruction source"
        );
    }

    @Test
    void ignoresAClaudeFileGitIgnores() {
        Path root = new GitFixture("entry-claude-ignored")
            .write(EntryFileRules.INSTRUCTIONS, AIRNESS + INSTRUCTION_BODY)
            .write(".gitignore", "CLAUDE.local.md\n")
            .write("CLAUDE.local.md", "Personal notes.\n")
            .root();
        assertTrue(
            Verdicts.clean(check(root).findings()),
            "an ignored personal file is not part of the tree the check governs"
        );
    }

    @Test
    void reportsEveryToolRuleDirectoryAndNestedGeminiFile() {
        Path root = new GitFixture("entry-tool-rules")
            .write(EntryFileRules.INSTRUCTIONS, AIRNESS + INSTRUCTION_BODY)
            .write(".clinerules/style.md", OWN_RULE)
            .write(".cursor/rules/java.mdc", OWN_RULE)
            .write("module/.cursor/rules/nested.mdc", OWN_RULE)
            .write(".windsurf/rules/tests.md", OWN_RULE)
            .write(".github/instructions/java.instructions.md", OWN_RULE)
            .write("module/GEMINI.md", OWN_RULE)
            .write("docs/Gemini.md", OWN_RULE)
            .root();
        assertEquals(
            Set.of(
                Path.of(".clinerules", "style.md").toString(),
                Path.of(".cursor", "rules", "java.mdc").toString(),
                Path.of("module", ".cursor", "rules", "nested.mdc").toString(),
                Path.of(".windsurf", "rules", "tests.md").toString(),
                Path.of(".github", "instructions", "java.instructions.md").toString(),
                Path.of("module", "GEMINI.md").toString(),
                Path.of("docs", "Gemini.md").toString()
            ),
            Set.copyOf(Verdicts.offences(check(root).findings(), "nested GEMINI.md files are forbidden")),
            "each form holds instructions of its own beside AGENTS.md, which no reference check reads"
        );
    }

    @Test
    void leavesRootReferenceFilesAndOtherToolFilesAlone() {
        Path root = new GitFixture("entry-tool-references")
            .write(EntryFileRules.INSTRUCTIONS, AIRNESS + INSTRUCTION_BODY)
            .write("GEMINI.md", "Read AGENTS.md.\n")
            .write(".clinerules", "Read AGENTS.md.\n")
            .write(".cursor/mcp.json", "{}\n")
            .write(".github/instructions/README.md", "Read AGENTS.md.\n")
            .write(".github/copilot-instructions.md", "Read AGENTS.md.\n")
            .root();
        assertTrue(
            Verdicts.clean(check(root).findings()),
            "a root reference file is checked with the managed files, and a tool's other settings hold no rules"
        );
    }
}
