package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies that every committed skill manifest is read and named with its defects.
 */
class SkillManifestCheckTest {

    @Test
    void namesTheDefectsOfEachManifestUnderTheSkillDirectory() {
        Path root = new GitFixture("skills-broken")
            .write(".claude/skills/release/SKILL.md", "---\nname: release\ndescription: Use when releasing.\n---\n")
            .write(".claude/skills/nested/deploy/SKILL.md", "# Deploy\n")
            .write("docs/SKILL.md", "# Not a skill location\n")
            .root();
        SkillManifestCheck check = new SkillManifestCheck(root);
        assertEquals(
            List.of(
                Path.of(".claude", "skills", "nested", "deploy", "SKILL.md")
                    + " [frontmatter] file must open with valid \"---\" frontmatter"
            ),
            Verdicts.offences(check.findings(), "frontmatter an agent cannot use"),
            "a nested manifest is read and a SKILL.md outside the skill directory is not"
        );
        assertEquals(2, check.scanned(), "both manifests under the skill directory are read");
    }
}
