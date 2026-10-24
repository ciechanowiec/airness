package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies the frontmatter rules an agent relies on to discover and invoke a skill.
 */
class SkillManifestTest {

    private static List<String> violations(String content, String directory) {
        return SkillManifest.parse(content).violations(directory);
    }

    @Test
    void acceptsAWellFormedManifestWithAFoldedDescription() {
        String content = """
            ---
            name: release-notes
            description: >-
              Drafts release notes from the commit history
              whenever the user asks for a changelog.
            metadata:
              owner: platform
            ---
            # Release notes
            """;
        assertEquals(List.of(), violations(content, "release-notes"), "a folded description is read whole");
    }

    @Test
    void acceptsAManifestWrittenWithWindowsLineEndings() {
        String content = "---\r\nname: lint\r\ndescription: Use when linting.\r\n---\r\nBody.\r\n";
        assertEquals(List.of(), violations(content, "lint"), "line endings do not hide the frontmatter");
    }

    @Test
    void reportsAManifestWithoutFrontmatter() {
        assertEquals(
            List.of("[frontmatter] file must open with valid \"---\" frontmatter"), violations("# Skill\n", "skill"),
            "no frontmatter means nothing else can be judged"
        );
    }

    @Test
    void reportsEveryNameDescriptionAndKeyDefect() {
        String content = """
            ---
            name: Release_Notes
            description: Drafts release notes.
            version: 2
            ---
            """;
        assertEquals(
            List.of(
                "[name] name \"Release_Notes\" must be kebab-case",
                "[name] name \"Release_Notes\" must equal parent directory \"release-notes\"",
                "[description] description must say when to use the skill (\"when ...\" clause)",
                "[keys] unknown frontmatter key \"version\""
            ),
            violations(content, "release-notes"),
            "each defect is reported on its own"
        );
    }

    @Test
    void reportsMissingNameAndDescription() {
        assertEquals(
            List.of(
                "[name] frontmatter has no \"name\" key", "[description] frontmatter has no \"description\" key"
            ),
            violations("---\nlicense: MIT\n---\n", "skill"),
            "an agent cannot find a skill with neither"
        );
    }
}
