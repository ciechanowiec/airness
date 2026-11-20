package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies the outline a Markdown document's headings draw and the sections they divide it into.
 */
class MarkdownOutlineTest {

    private static final String OUTLINE = "break the outline";
    private static final String SECTIONS = "are empty or hold";
    private static final String DOC = "doc.md";

    @Test
    void namesAHeadingThatSkipsALevelOnTheWayDown() {
        Path root = new GitFixture("markdown-skip")
            .write(DOC, "## Start\n\nText.\n\n#### Deep\n\nText.\n\n# Up\n\nText.\n\n## Down\n\nText.\n")
            .root();
        assertEquals(
            List.of("doc.md:5: the outline skips from level 2 to 4"), MarkdownCheckTest.offences(root, OUTLINE),
            "the outline may open at any level and climb any distance, but descends one level at a time"
        );
    }

    @Test
    void namesAnUnderlinedHeadingAnEmptyOneAndOneEndingInPunctuation() {
        Path root = new GitFixture("markdown-headings")
            .write(
                DOC, """
                    Title
                    =====

                    Text.

                    Part
                    ----

                    Text.

                    ## Steps:

                    Text.

                    ## Why?

                    Text.

                    ## Uses `a.b.`

                    Text.

                    ## Done.

                    Text.

                    ##

                    Text.
                    """
            ).root();
        assertEquals(
            List.of(
                "doc.md:1: the heading 'Title' is underlined; write it with #",
                "doc.md:6: the heading 'Part' is underlined; write it with #",
                "doc.md:11: the heading 'Steps:' ends with ':'", "doc.md:23: the heading 'Done.' ends with '.'",
                "doc.md:27: the heading has no text"
            ),
            MarkdownCheckTest.offences(root, OUTLINE),
            "a question and a heading ending in code are left alone"
        );
    }

    @Test
    void namesASiblingThatRepeatsATitle() {
        Path root = new GitFixture("markdown-siblings")
            .write(
                DOC, """
                    # Doc

                    ## Linux

                    ### Install

                    Text.

                    ### install

                    Text.

                    ## macOS

                    ### Install

                    Text.
                    """
            ).root();
        assertEquals(
            List.of("doc.md:9: the heading 'install' repeats a sibling's"), MarkdownCheckTest.offences(root, OUTLINE),
            "a title repeats only beside a sibling of the same section, whatever its case"
        );
    }

    @Test
    void namesAnEmptySectionAndOneHoldingASingleSubsection() {
        Path root = new GitFixture("markdown-sections")
            .write(
                DOC, """
                    # Doc

                    ## Empty

                    ## Commented

                    <!-- nothing yet -->

                    ## Lone

                    ### Only

                    Text.

                    ## Parent

                    ### First

                    Text.

                    ### Second

                    Text.

                    ## Last
                    """
            ).root();
        assertEquals(
            List.of(
                "doc.md:3: the section 'Empty' is empty", "doc.md:5: the section 'Commented' is empty",
                "doc.md:25: the section 'Last' is empty", "doc.md:9: the section 'Lone' holds a single subsection"
            ),
            MarkdownCheckTest.offences(root, SECTIONS),
            "a comment is no content, a section opening on its subsections is not empty, and two subsections pass"
        );
    }
}
