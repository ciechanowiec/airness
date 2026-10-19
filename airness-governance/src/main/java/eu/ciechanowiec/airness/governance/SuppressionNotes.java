package eu.ciechanowiec.airness.governance;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The parts of a suppression entry's {@code notes}: why the advisory can't be exploited here, the plan that
 * limits and then removes it, and the date the decision was recorded.
 *
 * <p>The scanner keeps one free-text element per entry, so the plan is written in the same element after a
 * {@code Mitigation:} marker. Reading both the check and the report from this one parser keeps them from
 * splitting the same notes two ways.
 *
 * @param reason     why this project can't be exploited through the advisory, without the date lead-in
 * @param mitigation what limits the risk now and what will remove the vulnerable package, or empty
 * @param addedOn    the first {@code YYYY-MM-DD} date in the notes, or empty
 */
public record SuppressionNotes(String reason, String mitigation, String addedOn) {

    /**
     * The marker that opens the mitigation plan inside the notes.
     */
    public static final String MARKER = "Mitigation:";

    static final Pattern DATE = Pattern.compile("\\b\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])\\b");
    private static final Pattern LEAD_IN = Pattern.compile("^Added \\d{4}-\\d{2}-\\d{2}[^.]*\\.\\s*");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * Splits one entry's notes.
     *
     * @param notes the text of the entry's {@code notes} element
     * @return the reason, mitigation plan and date it carries
     */
    public static SuppressionNotes parse(String notes) {
        String text = normalized(notes);
        int marker = text.indexOf(MARKER);
        String before = marker < 0 ? text : text.substring(0, marker).strip();
        String plan = marker < 0 ? "" : text.substring(marker + MARKER.length()).strip();
        Matcher date = DATE.matcher(text);
        return new SuppressionNotes(
            LEAD_IN.matcher(before).replaceFirst(""), plan, date.find() ? date.group() : ""
        );
    }

    /**
     * The notes as one line, the way they read once the XML indentation is gone.
     *
     * @param notes the text of a {@code notes} element
     * @return the text with every whitespace run collapsed to one space
     */
    public static String normalized(String notes) {
        return WHITESPACE.matcher(notes).replaceAll(" ").strip();
    }
}
