package eu.ciechanowiec.airness.governance;

import java.util.OptionalInt;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Reads the parts of one YAML line that decide how the lines after it nest, without parsing the document.
 *
 * <p>A quoted scalar can hold every character that means something to the block structure, a {@code #}, a
 * {@code :} or a bracket, so each question about structure is asked of the line with its quoted spans blanked
 * to spaces of their own length. A quotation mark opens a span only where a scalar can start, after
 * whitespace, a flow indicator or the start of the line, because one inside a plain scalar, as in
 * {@code it's}, is an ordinary character. Every offset of the blanked text is an offset of the line.
 */
@UtilityClass
final class YamlText {

    private static final Pattern QUOTED = Pattern.compile(
        "(?<![^\\s\\[{,])(?:\"(?:[^\"\\\\]|\\\\.)*+\"|'(?:[^']|'')*+')"
    );
    private static final Pattern COMMENT = Pattern.compile("(?:^|\\s)#");
    private static final Pattern MAPPING = Pattern.compile(":(?=\\s|$)");
    private static final Pattern PROPERTIES = Pattern.compile("^(?:[&!]\\S*\\s*)+");
    private static final Pattern MARKER = Pattern.compile("^(?:(?:---|\\.\\.\\.)(?:\\s.*)?|%.*)$");
    private static final Pattern BLOCK_SCALAR = Pattern.compile("[|>][-+1-9]{0,2}");
    private static final Pattern SEQUENCE_ENTRY = Pattern.compile("-(?: +|$)");
    private static final String SPACE = " ";
    private static final char COMMENT_START = '#';
    private static final String FLOW_OPENINGS = "[{";
    private static final String FLOW_CLOSINGS = "]}";

    /**
     * How many spaces open the line.
     *
     * @param line the line as written
     * @return its indentation in columns
     */
    static int indent(String line) {
        return line.length() - line.stripLeading().length();
    }

    /**
     * Whether the line carries nothing the structure reads: it is blank or holds only a comment.
     *
     * @param line the line as written
     * @return whether the line can be passed over
     */
    static boolean insignificant(String line) {
        String text = line.strip();
        return text.isEmpty() || text.charAt(0) == COMMENT_START;
    }

    /**
     * Whether the line starts or ends a document, or is a directive, any of which begins the nesting afresh.
     *
     * @param line the line as written
     * @return whether the line is a document marker or a directive
     */
    static boolean marker(CharSequence line) {
        return MARKER.matcher(line).matches();
    }

    /**
     * The line without its comment and without the blank space after what remains.
     *
     * @param line the line as written
     * @return the line as the structure reads it, quotation marks intact
     */
    static String uncommented(String line) {
        Matcher comment = COMMENT.matcher(blanked(line));
        String kept = comment.find() ? line.substring(0, comment.start()) : line;
        return kept.stripTrailing();
    }

    /**
     * Where the node of a line starts, past the sequence entries that open it.
     *
     * @param text the uncommented line
     * @param from the column to start from, which is the line's indentation
     * @return the column of the first character that is not a sequence entry, or the line's length
     */
    static int nodeStart(String text, int from) {
        Matcher entry = SEQUENCE_ENTRY.matcher(text).region(from, text.length());
        return entry.lookingAt() ? nodeStart(text, entry.end()) : from;
    }

    /**
     * Where the colon that separates a mapping key from its value is, outside every quoted span.
     *
     * @param node the node of a line, from where it starts
     * @return the offset of that colon, or nothing when the node is no mapping entry
     */
    static OptionalInt mappingIndicator(CharSequence node) {
        Matcher indicator = MAPPING.matcher(blanked(node));
        return indicator.find() ? OptionalInt.of(indicator.start()) : OptionalInt.empty();
    }

    /**
     * A value without the anchors and tags written ahead of it.
     *
     * @param value a value as written after its key or its sequence entry
     * @return the content of the value
     */
    static String withoutProperties(String value) {
        return PROPERTIES.matcher(value.strip()).replaceFirst("").strip();
    }

    /**
     * Whether a value opens a literal or a folded block scalar, with or without its indicators.
     *
     * @param value the content of a value
     * @return whether the lines below it are the scalar's text
     */
    static boolean blockScalar(CharSequence value) {
        return BLOCK_SCALAR.matcher(value).matches();
    }

    /**
     * Whether a value is written as a flow collection.
     *
     * @param value the content of a value
     * @return whether it opens with a bracket or a brace
     */
    static boolean flow(CharSequence value) {
        return !value.isEmpty() && FLOW_OPENINGS.indexOf(value.charAt(0)) >= 0;
    }

    /**
     * How many flow collections a text opens and leaves unclosed, outside its quoted spans and its comment.
     *
     * @param text a line, or the part of one
     * @return the openings less the closings, negative when the text closes more than it opens
     */
    static int balance(String text) {
        String read = blanked(uncommented(text));
        return (int) (read.chars().filter(character -> FLOW_OPENINGS.indexOf(character) >= 0).count()
            - read.chars().filter(character -> FLOW_CLOSINGS.indexOf(character) >= 0).count());
    }

    private static String blanked(CharSequence text) {
        return QUOTED.matcher(text).replaceAll(YamlText::spaces);
    }

    private static String spaces(MatchResult quoted) {
        return SPACE.repeat(quoted.group().length());
    }
}
