package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * Verifies what the Markdown check reads, and the helpers the tests of its rules share. Each rule's tests sit
 * beside the rule, and cover the spellings Markdown allows for the construct it reads.
 */
class MarkdownCheckTest {

    /**
     * A node project's managed block, as Airness writes it into every node project of a repository.
     */
    static final String MANAGED = """
        <!-- BEGIN AIRNESS MANAGED: node -->
        ## Airness node layer

        This directory is a node project, listed by its module, and follows the root instruction file first.
        <!-- END AIRNESS MANAGED: node -->
        """;
    private static final String DOC = "doc.md";
    private static final String GUIDE = "guide.md";
    private static final String GUIDE_TEXT = "# Guide\n\nThe guide.\n";
    private static final String LONG = "This paragraph is long enough to count as a copy when it appears again in"
        + " another document of the repository.";

    @Test
    void passesAWellFormedRepositoryAndCountsWhatItRead() {
        Path root = new GitFixture("markdown-clean")
            .write(GUIDE, GUIDE_TEXT)
            .write(
                DOC, """
                    ---
                    name: doc
                    description: Used when a document is wanted.
                    ---

                    # Document

                    Read [the guide](guide.md) and [its heading](guide.md#guide).

                    | Column | Other |
                    | --- | --- |
                    | a | b |

                    ```text
                    plain output
                    ```
                    """
            ).root();
        MarkdownCheck check = new MarkdownCheck(root);
        assertAll(
            () -> assertTrue(Verdicts.clean(check.findings()), "a well-formed repository passes every rule"),
            () -> assertEquals(2, check.scanned(), "both documents are read")
        );
    }

    @Test
    @SneakyThrows
    void passesOverASymbolicLinkToADocument() {
        Path root = new GitFixture("markdown-symlink")
            .write(
                ".agents/skills/tool/SKILL.md", "---\nname: tool\ndescription: Used when a tool is wanted.\n---\n\n"
                    + LONG + "\n"
            ).root();
        Files.createDirectories(root.resolve(".claude/skills/tool"));
        Files.createSymbolicLink(
            root.resolve(".claude/skills/tool/SKILL.md"), Path.of("../../../.agents/skills/tool/SKILL.md")
        );
        MarkdownCheck check = new MarkdownCheck(root);
        assertAll(
            () -> assertEquals(1, check.scanned(), "the link is not a second document"),
            () -> assertTrue(Verdicts.clean(check.findings()), "and so not a copy")
        );
    }

    /**
     * The offences of the one Markdown rule whose headline holds a fragment, read from a fresh check.
     *
     * @param root     the working tree
     * @param fragment a distinguishing part of the rule's headline
     * @return that rule's offences
     */
    static List<String> offences(Path root, CharSequence fragment) {
        return Verdicts.offences(new MarkdownCheck(root).findings(), fragment);
    }

    /**
     * One line of the same word, repeated.
     *
     * @param word  the word
     * @param count how many times it stands
     * @return the line, ending in a newline
     */
    static String words(String word, int count) {
        return String.join(" ", Collections.nCopies(count, word)) + "\n";
    }
}
