package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The ban on deferred-work markers reads every comment form for either word, whatever its case or punctuation.
 *
 * <p>The words appear only inside string literals here, which Checkstyle does not read as comments, so this
 * file passes the rule it tests.
 */
class TodoCommentCheckstyleTest {

    private static final String RULE = "TodoComment";
    private static final String MARKER = "COMMENT";
    // Assembled, because a one-line Javadoc spelled out here would trip the rule that expands every Javadoc.
    private static final String JAVADOC_OPENING = "/*" + "*";
    private static final String MARKED = """
        /**
         * Does a thing.
         */
        class Sample {
            COMMENT
            void run() {
            }
        }
        """;

    @Test
    void reportsEverySpellingOfEitherMarker(@TempDir Path directory) {
        List<String> comments = List.of(
            "// TODO: handle the empty case", "// TODO handle the empty case", "// todo handle the empty case",
            "// FIXME validate the input", "// FIXME: validate the input", "// fixme validate the input",
            "/* TODO handle the empty case */", "/* FIXME validate the input */", JAVADOC_OPENING
                + " FIXME validate the input. */"
        );

        assertEquals(
            List.of(), comments.stream().filter(comment -> findings(directory, comment) != 1).toList(),
            "each marker is reported once"
        );
    }

    @Test
    void leavesCommentsWithoutAWholeMarkerWordAlone(@TempDir Path directory) {
        List<String> comments = List.of(
            "// Handles the empty case", "// Reads the todos table", "// Fixes the order of the menu"
        );

        assertEquals(
            List.of(), comments.stream().filter(comment -> findings(directory, comment) != 0).toList(),
            "no comment without a whole marker word is reported"
        );
    }

    private static int findings(Path directory, String comment) {
        return CheckstyleRule.findings(directory, MARKED.replace(MARKER, comment), RULE);
    }
}
