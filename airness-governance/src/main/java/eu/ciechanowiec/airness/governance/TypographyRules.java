package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;

/**
 * Scans already-decoded text for the typographic code points the guideline bans: the em dash, the en
 * dash, the single-character ellipsis, the typographic (curly) quotation marks, and the spaces a reader
 * cannot tell from an ordinary one or cannot see at all. The plain ASCII equivalents (the hyphen, three
 * periods, the straight quotation mark and the ordinary space) stand in their place. An invisible space
 * makes two lines that read the same compare as different, so a search, a diff or a duplicate check
 * misses what a reader would match. The joiners that compose an emoji or a word of a script that needs
 * them stay allowed, being part of the character they join. The scan takes decoded text and never
 * touches the filesystem, so it stays a pure, unit-testable rule.
 */
@UtilityClass
final class TypographyRules {

    static final int EM_DASH = 0x2014;
    static final int EN_DASH = 0x2013;
    static final int ELLIPSIS = 0x2026;
    static final int LEFT_SINGLE_QUOTE = 0x2018;
    static final int RIGHT_SINGLE_QUOTE = 0x2019;
    static final int LEFT_DOUBLE_QUOTE = 0x201C;
    static final int RIGHT_DOUBLE_QUOTE = 0x201D;
    static final int LOW_DOUBLE_QUOTE = 0x201E;
    static final int NO_BREAK_SPACE = 0x00A0;
    static final int FIGURE_SPACE = 0x2007;
    static final int ZERO_WIDTH_SPACE = 0x200B;
    static final int NARROW_NO_BREAK_SPACE = 0x202F;
    static final int WORD_JOINER = 0x2060;
    static final int ZERO_WIDTH_NO_BREAK_SPACE = 0xFEFF;
    private static final Set<Integer> BANNED = Set.of(
        EM_DASH, EN_DASH, ELLIPSIS, LEFT_SINGLE_QUOTE, RIGHT_SINGLE_QUOTE,
        LEFT_DOUBLE_QUOTE, RIGHT_DOUBLE_QUOTE, LOW_DOUBLE_QUOTE, NO_BREAK_SPACE, FIGURE_SPACE,
        ZERO_WIDTH_SPACE, NARROW_NO_BREAK_SPACE, WORD_JOINER, ZERO_WIDTH_NO_BREAK_SPACE
    );
    private static final String NEWLINE = "\n";

    static List<TypographyViolation> findViolations(String content) {
        String[] lines = content.split(NEWLINE, -1);
        return IntStream.range(0, lines.length)
            .boxed()
            .flatMap(index -> violationsInLine(lines[index], index + 1).stream())
            .toList();
    }

    static boolean isBanned(int codePoint) {
        return BANNED.contains(codePoint);
    }

    private static List<TypographyViolation> violationsInLine(String line, int lineNumber) {
        return IntStream.range(0, line.length())
            .filter(index -> codePointStartsAt(line, index))
            .filter(index -> isBanned(line.codePointAt(index)))
            .mapToObj(
                index -> new TypographyViolation(
                    lineNumber, line.codePointCount(0, index) + 1, line.codePointAt(index)
                )
            ).toList();
    }

    private static boolean codePointStartsAt(CharSequence line, int index) {
        return index == 0
            || !Character.isLowSurrogate(line.charAt(index))
            || !Character.isHighSurrogate(line.charAt(index - 1));
    }
}
