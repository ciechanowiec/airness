package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The ban on escaped characters admits the escape of each invisible space the typography check refuses raw,
 * in every literal form and in either case of its digits, as long as every escape in a literal belongs to one
 * of the rule's two sets. It refuses a literal mixing those sets and the escape of a printable character.
 *
 * <p>Each escape is written here behind an escaped backslash, which Checkstyle strips before it looks for an
 * escape, so this file passes the rule it tests.
 */
class EscapedUnicodeCheckstyleTest {

    private static final String RULE = "AvoidEscapedUnicodeCharacters";
    private static final String MARKER = "LITERAL";
    private static final String SAMPLE = """
        class Sample {
            Object value = LITERAL;
        }
        """;
    private static final String QUOTE = "\"";
    private static final String APOSTROPHE = "'";
    private static final String TEXT_BLOCK_OPENING = "\"\"\"\n        254";
    private static final String TEXT_BLOCK_CLOSING = "nm\n        \"\"\"";
    private static final String NO_BREAK_SPACE = "\\u00A0";
    private static final String FIGURE_SPACE = "\\u2007";
    private static final String ZERO_WIDTH_SPACE = "\\u200B";
    private static final String NARROW_NO_BREAK_SPACE = "\\u202F";
    private static final String WORD_JOINER = "\\u2060";
    private static final String ZERO_WIDTH_NO_BREAK_SPACE = "\\uFEFF";
    private static final String ACCENTED_LETTER = "\\u00E9";
    private static final String LATIN_LETTER = "\\u0041";

    @Test
    void admitsTheEscapeOfEachInvisibleSpaceInEveryLiteralForm(@TempDir Path directory) {
        List<String> literals = Stream.of(
            NO_BREAK_SPACE, FIGURE_SPACE, ZERO_WIDTH_SPACE, NARROW_NO_BREAK_SPACE, WORD_JOINER,
            ZERO_WIDTH_NO_BREAK_SPACE
        ).flatMap(escape -> Stream.of(escape, escape.toLowerCase(Locale.ROOT)))
            .flatMap(EscapedUnicodeCheckstyleTest::literalForms)
            .toList();

        assertEquals(
            List.of(), literals.stream().filter(literal -> findings(directory, literal) != 0).toList(),
            "no escape of an invisible space is reported"
        );
    }

    @Test
    void admitsSeveralEscapesOfOneSetAndAConcatenationAcrossTheSets(@TempDir Path directory) {
        List<String> literals = List.of(
            quoted(NO_BREAK_SPACE + FIGURE_SPACE + NARROW_NO_BREAK_SPACE + ZERO_WIDTH_NO_BREAK_SPACE),
            quoted(ZERO_WIDTH_SPACE + WORD_JOINER + ZERO_WIDTH_NO_BREAK_SPACE),
            quoted(NO_BREAK_SPACE) + " + " + quoted(ZERO_WIDTH_SPACE)
        );

        assertEquals(
            List.of(), literals.stream().filter(literal -> findings(directory, literal) != 0).toList(),
            "no literal whose escapes share one set is reported"
        );
    }

    @Test
    void refusesAPrintableEscapeAndALiteralMixingTheTwoSets(@TempDir Path directory) {
        List<String> literals = Stream.concat(
            Stream.of(ACCENTED_LETTER, LATIN_LETTER).flatMap(EscapedUnicodeCheckstyleTest::literalForms),
            Stream.of(
                quoted(NO_BREAK_SPACE + ACCENTED_LETTER), quoted(NO_BREAK_SPACE + ZERO_WIDTH_SPACE),
                quoted(WORD_JOINER + FIGURE_SPACE), quoted(NARROW_NO_BREAK_SPACE + " 254 " + ZERO_WIDTH_SPACE),
                quoted(NO_BREAK_SPACE.replace("u", "uu"))
            )
        ).toList();

        assertEquals(
            List.of(), literals.stream().filter(literal -> findings(directory, literal) != 1).toList(),
            "each literal is reported once"
        );
    }

    private static Stream<String> literalForms(String escape) {
        return Stream.of(
            quoted(escape), APOSTROPHE + escape + APOSTROPHE, quoted("254" + escape + "nm"),
            TEXT_BLOCK_OPENING + escape + TEXT_BLOCK_CLOSING
        );
    }

    private static String quoted(String content) {
        return QUOTE + content + QUOTE;
    }

    private static int findings(Path directory, String literal) {
        return CheckstyleRule.findings(directory, SAMPLE.replace(MARKER, literal), RULE);
    }
}
