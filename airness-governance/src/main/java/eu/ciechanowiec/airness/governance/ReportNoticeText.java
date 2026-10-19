package eu.ciechanowiec.airness.governance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * A license or notice file set as paragraphs, with its words and their order unchanged.
 *
 * <p>License files are wrapped at a fixed width for a terminal, and keeping those breaks in a proportional
 * column leaves every line ragged. So the breaks inside a paragraph are removed, and the ones that start
 * something new, such as a list item, a copyright statement or a heading, are kept.
 */
@UtilityClass
final class ReportNoticeText {

    private static final Pattern BLOCK = Pattern.compile("\\n[ \\t]*\\n");
    private static final Pattern QUOTE_MARKERS = Pattern.compile("^[ \\t]*(?:>[ \\t]?)+");
    private static final Pattern HEADING = Pattern.compile("^#{1,6}[ \\t]+");
    private static final Pattern RULE = Pattern.compile("^[-=_*~]{3,}$");
    private static final Pattern LIST_ITEM = Pattern.compile(
        "^(?:[-*+\\x{2022}]\\s|\\(?(?:\\d{1,3}|[a-z]|[ivx]{2,4})[.)]\\s)", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern COPYRIGHT_LINE = Pattern.compile(
        "^(?:copyright\\s+(?:\\(c\\)|\\x{A9}|\\d{4})|\\x{A9})", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FIELD_LINE = Pattern.compile("^[A-Z][A-Za-z]{1,20}(?: [A-Za-z]{1,20})?:[ \\t]");
    private static final Pattern SPACES = Pattern.compile("[ \\t]+");
    private static final Pattern LOWERCASE = Pattern.compile("\\p{Ll}");
    private static final Pattern LETTER = Pattern.compile("\\p{L}");
    private static final Pattern HYPHENATED_END = Pattern.compile("\\p{L}-$");
    private static final Pattern LOWERCASE_START = Pattern.compile("^\\p{Ll}");
    private static final int HEADING_LENGTH = 40;
    // A web or e-mail address cannot break between words, and a copyright statement is a short line of
    // names, so justifying either opens wide gaps. Such a paragraph is set flush left instead.
    private static final Pattern FLUSH_LEFT = Pattern.compile(
        "\\b(?:https?://|www\\.)\\S{20}|<[^<>\\s]{1,80}@[^<>\\s]{1,80}>|^(?:\\(c\\)|copyright\\b|\\x{A9})",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Reflows a license file for a proportional column.
     *
     * @param text the file as shipped
     * @return the text with paragraphs separated by a blank line and kept line breaks by a newline
     */
    static String reflow(String text) {
        String unquoted = text.replace("\r\n", "\n").lines().map(line -> QUOTE_MARKERS.matcher(line).replaceFirst(""))
            .collect(Collectors.joining("\n")).strip();
        return BLOCK.splitAsStream(unquoted).map(ReportNoticeText::joined).filter(block -> !block.isEmpty())
            .collect(Collectors.joining("\n\n"));
    }

    /**
     * Sets reflowed text as paragraphs.
     *
     * @param text the text {@link #reflow(String)} returned
     * @return the paragraph markup
     */
    static String render(String text) {
        return Arrays.stream(text.split("\n\n", -1)).map(ReportNoticeText::block).collect(Collectors.joining());
    }

    private static String joined(String block) {
        List<String> lines = new ArrayList<>();
        block.lines().map(line -> SPACES.matcher(line.strip()).replaceAll(" ")).filter(line -> !line.isEmpty())
            .forEach(line -> append(lines, line));
        return String.join("\n", lines);
    }

    private static void append(List<String> lines, String line) {
        if (lines.isEmpty() || starts(lines.getLast(), line)) {
            lines.add(line);
            return;
        }
        // A word broken at a hyphen is rejoined without a space, so "non-" and "exclusive" read
        // "non-exclusive".
        String previous = lines.removeLast();
        boolean hyphenated = HYPHENATED_END.matcher(previous).find() && LOWERCASE_START.matcher(line).find();
        lines.add(previous + (hyphenated ? "" : " ") + line);
    }

    // The line announces something new, or the line before it stands alone, so nothing continues it.
    private static boolean starts(String previous, String line) {
        return opens(line) || standsAlone(previous);
    }

    private static boolean opens(String line) {
        return LIST_ITEM.matcher(line).find() || COPYRIGHT_LINE.matcher(line).find()
            || FIELD_LINE.matcher(line).find() || marker(line);
    }

    private static boolean standsAlone(String line) {
        return FIELD_LINE.matcher(line).find() || marker(line) || shouted(line);
    }

    private static boolean marker(String line) {
        return heading(line) || rule(line);
    }

    // A short line with letters and no lowercase one is a heading such as PREAMBLE, not a sentence.
    private static boolean shouted(String line) {
        return line.length() < HEADING_LENGTH && LETTER.matcher(line).find() && !LOWERCASE.matcher(line).find();
    }

    // A bundle of licenses titles each one with a Markdown heading and separates them with a ruled line.
    // Those two are drawn as what they are, not printed as characters.
    private static String block(String block) {
        List<String> lines = block.lines().toList();
        if (lines.stream().anyMatch(line -> rule(line) || heading(line))) {
            return "<div class=\"license-entry\">" + lines.stream().map(ReportNoticeText::line)
                .collect(Collectors.joining()) + "</div>";
        }
        String alignment = FLUSH_LEFT.matcher(block).find() ? " class=\"ragged\"" : "";
        return "<p" + alignment + ">" + lines.stream().map(ReportMarkup::escape).collect(Collectors.joining("<br>"))
            + "</p>";
    }

    private static String line(String line) {
        if (rule(line)) {
            return "<hr>";
        }
        return heading(line)
            ? "<p class=\"license-heading\">" + ReportMarkup.escape(HEADING.matcher(line).replaceFirst("")) + "</p>"
            : ReportMarkup.paragraph(line);
    }

    private static boolean heading(String line) {
        return HEADING.matcher(line).find();
    }

    private static boolean rule(String line) {
        return RULE.matcher(line).matches();
    }
}
