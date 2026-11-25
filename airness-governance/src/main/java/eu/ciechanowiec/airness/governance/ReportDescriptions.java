package eu.ciechanowiec.airness.governance;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * An advisory's description as a reviewer reads it: whole sentences, and a mark wherever the report cuts one short.
 */
@UtilityClass
final class ReportDescriptions {

    private static final Pattern SENTENCE = Pattern.compile("^(.{1,300}?[.!?])(?:\\s|$)", Pattern.DOTALL);
    private static final int SUMMARY_LENGTH = 300;
    private static final String CUT = " [...]";

    /**
     * The description's first sentence, which a reviewer triages by, as the title of a database entry would be. A
     * description without a sentence end in reach is cut at a word.
     *
     * @param description the advisory's description, often several paragraphs
     * @return the summary
     */
    static String summary(String description) {
        String text = SuppressionNotes.normalized(description);
        Matcher sentence = SENTENCE.matcher(text);
        return sentence.find() ? sentence.group(1) : shortened(text, SUMMARY_LENGTH);
    }

    /**
     * A text cut at a word boundary within a length, with the cut marked.
     *
     * @param text   the text
     * @param length the most characters kept
     * @return the text, whole when it fits
     */
    static String shortened(String text, int length) {
        if (text.length() <= length) {
            return text;
        }
        int space = text.lastIndexOf(' ', length);
        return text.substring(0, space > 0 ? space : length).stripTrailing() + CUT;
    }
}
