package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The typography scan flags each banned code point with its line and column and ignores plain ASCII.
 */
class TypographyRulesTest {

    private static final int ROCKET = 0x1F680;
    private static final int WOMAN = 0x1F469;
    private static final int GIRL = 0x1F467;
    private static final int ZERO_WIDTH_JOINER = 0x200D;
    private static final int ZERO_WIDTH_NON_JOINER = 0x200C;

    @Test
    void flagsEmDashWithLineAndColumn() {
        String content = "alpha " + Character.toString(TypographyRules.EM_DASH) + " beta";
        List<TypographyViolation> violations = TypographyRules.findViolations(content);
        assertEquals(1, violations.size());
        assertEquals(TypographyRules.EM_DASH, violations.getFirst().codePoint());
        assertEquals(7, violations.getFirst().column());
    }

    @Test
    void reportsLineNumbersAcrossNewlines() {
        String content = "clean line\nsecond " + Character.toString(TypographyRules.ELLIPSIS);
        List<TypographyViolation> violations = TypographyRules.findViolations(content);
        assertEquals(1, violations.size());
        assertEquals(2, violations.getFirst().lineNumber());
    }

    @Test
    void acceptsPlainAsciiEquivalents() {
        String content = "plain - hyphen, three periods ... and \"straight quotes\"";
        assertTrue(TypographyRules.findViolations(content).isEmpty());
    }

    @Test
    void flagsEveryBannedCodePoint() {
        List<Integer> banned = List.of(
            TypographyRules.EM_DASH, TypographyRules.EN_DASH, TypographyRules.ELLIPSIS,
            TypographyRules.LEFT_SINGLE_QUOTE, TypographyRules.RIGHT_SINGLE_QUOTE,
            TypographyRules.LEFT_DOUBLE_QUOTE, TypographyRules.RIGHT_DOUBLE_QUOTE,
            TypographyRules.LOW_DOUBLE_QUOTE, TypographyRules.NO_BREAK_SPACE, TypographyRules.FIGURE_SPACE,
            TypographyRules.ZERO_WIDTH_SPACE, TypographyRules.NARROW_NO_BREAK_SPACE, TypographyRules.WORD_JOINER,
            TypographyRules.ZERO_WIDTH_NO_BREAK_SPACE
        );
        assertTrue(banned.stream().allMatch(TypographyRules::isBanned));
    }

    @Test
    void locatesASpaceThatLooksOrdinaryOrCannotBeSeen() {
        String content = "price" + Character.toString(TypographyRules.NO_BREAK_SPACE) + "10\nword"
            + Character.toString(TypographyRules.ZERO_WIDTH_SPACE) + "break";
        assertEquals(
            List.of(
                new TypographyViolation(1, 6, TypographyRules.NO_BREAK_SPACE),
                new TypographyViolation(2, 5, TypographyRules.ZERO_WIDTH_SPACE)
            ),
            TypographyRules.findViolations(content)
        );
    }

    @Test
    void keepsTheJoinersThatComposeACharacter() {
        String family = Character.toString(WOMAN) + Character.toString(ZERO_WIDTH_JOINER) + Character.toString(GIRL);
        String unjoined = "a" + Character.toString(ZERO_WIDTH_NON_JOINER) + "b";
        assertTrue(
            TypographyRules.findViolations(family + " " + unjoined).isEmpty(),
            "the zero-width joiner and non-joiner are part of the emoji and the word they join"
        );
    }

    @Test
    void countsColumnsByCodePointRatherThanUtf16Unit() {
        String supplementary = Character.toString(ROCKET);
        assertEquals(
            List.of(new TypographyViolation(1, 2, TypographyRules.EM_DASH)),
            TypographyRules.findViolations(supplementary + Character.toString(TypographyRules.EM_DASH))
        );
    }
}
