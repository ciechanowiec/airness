package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a repository states no Markdown text in two places, and that neither a managed block nor the
 * agent-rules block Next.js writes is a copy.
 */
class MarkdownDuplicatesTest {

    private static final String COPIES = "more than one place";
    private static final String NEXT_BLOCK = """
        <!-- BEGIN:nextjs-agent-rules -->

        ## This is NOT the Next.js you know

        This version has breaking changes, so read the relevant guide in the installed package before writing code.

        <!-- END:nextjs-agent-rules -->
        """;
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

    @Test
    void leavesTheNextAgentRulesBlockOutOfTheCopies() {
        Path root = new GitFixture("markdown-next-block")
            .write("web/AGENTS.md", "# Web\n\nThe public site.\n\n" + NEXT_BLOCK)
            .write("cms/AGENTS.md", "# Content\n\nThe content service.\n\n" + NEXT_BLOCK)
            .write("api/AGENTS.md", NEXT_BLOCK)
            .write("app/AGENTS.md", NEXT_BLOCK)
            .root();
        assertEquals(
            List.of(), MarkdownCheckTest.offences(root, COPIES),
            "the block next dev writes into every application is neither a file copy nor a paragraph copy"
        );
    }

    @Test
    void comparesTextUnderAnyOtherMarkers() {
        String marked = "<!-- BEGIN:shared-rules -->\n\n" + LONG + "\n\n<!-- END:shared-rules -->\n";
        Path root = new GitFixture("markdown-other-block")
            .write("a.md", "# A\n\n" + marked)
            .write("b.md", "# B\n\n" + marked)
            .root();
        assertEquals(
            List.of("b.md:5: the paragraph copies a.md:5"), MarkdownCheckTest.offences(root, COPIES),
            "only the Next.js block is left out, so text under markers of any other name is still compared"
        );
    }
}
