package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies the shape of a Markdown document's code, lists and paragraphs.
 */
class MarkdownBlocksTest {

    private static final String CODE = "indented or names no language";
    private static final String LISTS = "nested three levels";
    private static final String LINES = "cut across lines";
    private static final String DOC = "doc.md";

    @Test
    void namesIndentedCodeAndAFenceThatNamesNoLanguage() {
        Path root = new GitFixture("markdown-code")
            .write(
                DOC, """
                    Text.

                        indented

                    ```
                    backticks
                    ```

                    ~~~
                    tildes
                    ~~~

                    ```text
                    named
                    ```

                    ~~~bash
                    named
                    ~~~
                    """
            ).root();
        assertEquals(
            List.of(
                "doc.md:3: the code block is indented; fence it", "doc.md:5: the code fence names no language",
                "doc.md:9: the code fence names no language"
            ),
            MarkdownCheckTest.offences(root, CODE)
        );
    }

    @Test
    void namesAListNestedThreeLevelsDeepAndAListOfOneItem() {
        Path root = new GitFixture("markdown-lists")
            .write(
                DOC, """
                    - one
                      1. two
                         - three
                         - three again
                      2. two again
                    - one again

                    Single:

                    - alone

                    Ordered:

                    1. alone
                    """
            ).root();
        assertEquals(
            List.of(
                "doc.md:3: the list is nested three levels deep", "doc.md:10: the list holds a single item",
                "doc.md:14: the list holds a single item"
            ),
            MarkdownCheckTest.offences(root, LISTS),
            "bullet and ordered lists count alike toward the depth and toward a single item"
        );
    }

    @Test
    void namesAParagraphCutAcrossLinesWhereverItStands() {
        Path root = new GitFixture("markdown-lines")
            .write(
                DOC, """
                    One paragraph
                    over three
                    lines.

                    - an item
                    continued lazily

                    > a quotation
                    > over two lines

                    Two spaces\s\s
                    break invisibly.

                    A backslash\\
                    breaks visibly.

                    An escaped backslash\\\\
                    does not.

                    | A | B |
                    | --- | --- |
                    | 1 | 2 |

                    ```text
                    code
                    over lines
                    ```
                    """
            ).root();
        assertEquals(
            List.of(
                "doc.md:1: the paragraph is cut across lines (join 2 line break(s) into spaces)",
                "doc.md:5: the paragraph is cut across lines (join 1 line break(s) into spaces)",
                "doc.md:8: the paragraph is cut across lines (join 1 line break(s) into spaces)",
                "doc.md:11: the paragraph is cut across lines (join 1 line break(s) into spaces)",
                "doc.md:17: the paragraph is cut across lines (join 1 line break(s) into spaces)"
            ),
            MarkdownCheckTest.offences(root, LINES),
            "a list item, a quotation and two trailing spaces are cut like any paragraph, and a backslash is a break"
        );
    }
}
