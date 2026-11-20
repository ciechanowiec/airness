package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a repository states no Markdown text in two places, and that a managed block is no copy.
 */
class MarkdownDuplicatesTest {

    private static final String COPIES = "more than one place";
    private static final String LONG = "This paragraph is long enough to count as a copy when it appears again in"
        + " another document of the repository.";

    @Test
    void namesAFileThatCopiesAnotherOnceAndAParagraphCopiedIntoAnotherFile() {
        Path root = new GitFixture("markdown-copies")
            .write("a.md", "# A\n\n" + LONG + "\n\nShort.\n")
            .write("b.md", "# A\n\n" + LONG + "\n\nShort.\n")
            .write("c.md", "# C\n\n*" + LONG.toUpperCase(Locale.ROOT) + "*\n\nShort.\n")
            .root();
        assertEquals(
            List.of("b.md: the file copies a.md", "c.md:3: the paragraph copies a.md:3"), MarkdownCheckTest.offences(
                root, COPIES
            ),
            "a whole copy is named once, a paragraph is matched whatever its emphasis and case, and a short one never"
        );
    }

    @Test
    void leavesTheManagedBlocksOutOfTheCopies() {
        Path root = new GitFixture("markdown-managed")
            .write("web/AGENTS.md", MarkdownCheckTest.MANAGED)
            .write("api/AGENTS.md", MarkdownCheckTest.MANAGED)
            .write("cms/AGENTS.md", MarkdownCheckTest.MANAGED + "\nThe content service.\n")
            .root();
        assertEquals(
            List.of(), MarkdownCheckTest.offences(root, COPIES),
            "the blocks Airness writes into every node project are neither file copies nor paragraph copies"
        );
    }
}
