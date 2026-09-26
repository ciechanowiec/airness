package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Where a stylesheet writes a space that reads as a combinator rather than as a wrap.
 *
 * <p>A space between two parts of a selector means one is inside the other. A selector written over
 * two lines therefore selects something other than what was written, because the break is a space and
 * the space is a combinator. The rule is valid, the file formats, and what it paints is not what the
 * author named.
 *
 * <p>What is reported is one shape of that mistake rather than every space before a pseudo-class,
 * because most of those are somebody meaning exactly what they wrote. A group written inside one is
 * ordinary, so a table naming its own cells is left alone. A chain of refusals broken in the middle is
 * not: nothing is gained by refusing one thing about a link and another about whatever sits inside it,
 * and a chain long enough to wrap is exactly the chain a line budget breaks.
 *
 * <p>The break itself is gone by the time this reads the file. A formatter joins the halves back onto
 * one line and keeps the space, which leaves the mistake reading like a decision, so this looks for
 * what the join leaves behind rather than for a line that is too long.
 */
@UtilityClass
final class StylesheetSelectorRules {

    /**
     * A refusal, then a space, then another refusal. The first is allowed one level of nesting inside
     * it, which is as deep as the published stylesheets of this fleet write one.
     */
    private static final Pattern BROKEN = Pattern.compile(
        ":not\\((?:[^()]|\\([^()]*\\))*\\)\\s+:not\\("
    );

    /**
     * Comments and quoted text, which are read out before any selector is, so that prose naming the
     * construct is not reported as writing it.
     */
    private static final Pattern UNREAD = Pattern.compile("/\\*.*?\\*/|\"[^\"\\n]*\"|'[^'\\n]*'", Pattern.DOTALL);

    private static final char NEWLINE = '\n';

    /**
     * Answers every place the given stylesheet joins two refusals with a space.
     *
     * @param content the stylesheet as it was written
     * @return the line of each, in the order they were written
     */
    static List<Integer> broken(CharSequence content) {
        String readable = readable(content);
        return BROKEN.matcher(readable).results().map(hit -> JavaCode.lineOf(readable, hit.start())).toList();
    }

    // Everything a selector cannot be written in, blanked rather than removed, so that what is left
    // sits at the offset it was read from and still answers for the line it was written on.
    private static String readable(CharSequence content) {
        Matcher unread = UNREAD.matcher(content);
        StringBuilder readable = new StringBuilder(content);
        while (unread.find()) {
            blank(readable, unread.start(), unread.end());
        }
        return readable.toString();
    }

    private static void blank(StringBuilder readable, int from, int to) {
        for (int index = from; index < to; index++) {
            char written = readable.charAt(index);
            readable.setCharAt(index, written == NEWLINE ? NEWLINE : ' ');
        }
    }
}
