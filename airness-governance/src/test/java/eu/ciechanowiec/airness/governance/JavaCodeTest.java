package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Blanking keeps the width and the line breaks of the source it reads, removes what a rule about
 * structure must not see, and differs between its two forms only over a literal that fits on one line.
 */
class JavaCodeTest {

    private static final String QUOTING_SOURCE = """
        package sample;

        class Subject {

            private static final String FIXTURE = ""\"
                @Test
                void quoted() {
                }
                ""\";
        }
        """;

    private static final String COMMENTED = """
        package sample;

        // assertEquals(1, 1) named in a comment
        class Subject {

            private static final String NAME = "assertEquals(1, 1)";
        }
        """;

    @Test
    void keepsTheWidthOfWhatItBlanks() {
        assertEquals(
            QUOTING_SOURCE.length(),
            JavaCode.blanked(QUOTING_SOURCE).length(),
            "an offset into the result has to be the same offset into the source"
        );
    }

    @Test
    void keepsEveryLineBreak() {
        assertEquals(
            QUOTING_SOURCE.lines().count(),
            JavaCode.blanked(QUOTING_SOURCE).lines().count(),
            "a line number taken from the result has to be the line number of the source"
        );
    }

    @Test
    void removesATestQuotedInsideATextBlock() {
        assertFalse(
            JavaCode.blanked(QUOTING_SOURCE).contains("@Test"),
            "a fixture quoting a source is not a declaration of the file that quotes it"
        );
    }

    @Test
    void removesAQuotedTestFromTheFormThatKeepsOneLineLiterals() {
        assertFalse(
            JavaCode.withoutComments(QUOTING_SOURCE).contains("@Test"),
            "a text block carries a quoted source in either form"
        );
    }

    @Test
    void keepsAOneLineLiteralOutsideOfAComment() {
        String readable = JavaCode.withoutComments(COMMENTED);
        assertTrue(readable.contains("\"assertEquals(1, 1)\""), "the operands of a call are read from a literal");
        assertFalse(readable.contains("named in a comment"), "while the comment above it is not code");
    }

    @Test
    void countsTheLineAnOffsetFallsOn() {
        assertEquals(1, JavaCode.lineOf(COMMENTED, 0), "the first line is one rather than zero");
        assertEquals(
            3,
            JavaCode.lineOf(COMMENTED, COMMENTED.indexOf("// assertEquals")),
            "and the comment sits on the third"
        );
    }
}
