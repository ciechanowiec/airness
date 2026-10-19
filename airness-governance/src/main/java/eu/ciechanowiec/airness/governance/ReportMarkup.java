package eu.ciechanowiec.airness.governance;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.unbescape.html.HtmlEscape;

/**
 * Small markup builders the report is assembled from. Every value that reaches the document from a
 * repository, a scanner or a license file passes through {@link #escape(String)} here.
 */
@UtilityClass
final class ReportMarkup {

    private static final DateTimeFormatter MOMENT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
        .withZone(ZoneOffset.UTC);
    private static final Map<String, String> ICONS = Map.of(
        "warning",
        "M12 2.5 1.5 21h21L12 2.5Zm-1.1 6.5h2.2l-.35 6.6h-1.5L10.9 9Zm1.1 10a1.3 1.3 0 1 1 0-2.6 1.3 1.3 0 0 1 0 2.6Z",
        "ok",
        "M12 1.5a10.5 10.5 0 1 0 0 21 10.5 10.5 0 0 0 0-21Zm-1.6 15.2-4.7-4.7 1.6-1.6 3.1 3.1 6.3-6.3 1.6 1.6-7.9 7.9Z",
        "fail",
        "M12 1.5a10.5 10.5 0 1 0 0 21 10.5 10.5 0 0 0 0-21Zm-1.1 5h2.2l-.35 7.6h-1.5L10.9 6.5Zm1.1 11.6a1.3 1.3 0 1 1 "
            + "0-2.6 1.3 1.3 0 0 1 0 2.6Z"
    );

    static String escape(String text) {
        return HtmlEscape.escapeHtml5(text);
    }

    /**
     * A moment as a reader outside the project reads it, such as {@code 2026-10-17 20:22:48 UTC}.
     *
     * @param moment an ISO-8601 instant, or any other text
     * @return the escaped moment, reformatted when it is an instant
     */
    static String moment(String moment) {
        try {
            return escape(MOMENT.format(Instant.parse(moment)));
        } catch (DateTimeParseException _) {
            return escape(moment);
        }
    }

    static String paragraph(String text) {
        return "<p>" + escape(text) + "</p>";
    }

    /**
     * Plain paragraphs of the report's own prose.
     *
     * @param items each paragraph, already rendered
     * @return the paragraph markup
     */
    static String paragraphs(List<String> items) {
        return items.stream().map(item -> "<p>" + item + "</p>").collect(Collectors.joining());
    }

    static String empty(String text) {
        return "<p class=\"empty\">" + escape(text) + "</p>";
    }

    static String code(String text) {
        return "<code>" + escape(text) + "</code>";
    }

    static String nowrap(String html) {
        return "<span class=\"nowrap\">" + html + "</span>";
    }

    static String mono(String text) {
        return "<span class=\"mono\">" + escape(text) + "</span>";
    }

    static String digest(String text) {
        return "<span class=\"digest\">" + escape(text) + "</span>";
    }

    /**
     * One table row of already rendered cells.
     *
     * @param cells each cell's markup
     * @return the row markup
     */
    static String row(Collection<String> cells) {
        return "<tr>" + cells.stream().map(cell -> "<td>" + cell + "</td>").collect(Collectors.joining()) + "</tr>";
    }

    /**
     * A ruled table, or a sentence saying it would be empty.
     *
     * @param headings the column headings
     * @param rows     the rendered rows
     * @param empty    what to say when there are no rows
     * @return the table markup
     */
    static String table(Collection<String> headings, List<String> rows, String empty) {
        if (rows.isEmpty()) {
            return empty(empty);
        }
        String header = headings.stream().map(heading -> "<th scope=\"col\">" + escape(heading) + "</th>")
            .collect(Collectors.joining());
        return "<div class=\"table-wrap\"><table class=\"tableblock\"><thead><tr>" + header
            + "</tr></thead><tbody>" + String.join("", rows) + "</tbody></table></div>";
    }

    /**
     * A two-column table of labelled facts.
     *
     * @param rows    each already rendered label with its already rendered value
     * @param variant an extra class for the table, such as {@code plan}, or empty
     * @return the table markup
     */
    static String facts(List<Map.Entry<String, String>> rows, String variant) {
        String kind = variant.isEmpty() ? "tableblock facts" : "tableblock facts " + variant;
        return "<div class=\"table-wrap\"><table class=\"" + kind + "\"><tbody>" + rows.stream()
            .map(entry -> "<tr><td>" + entry.getKey() + "</td><td>" + entry.getValue() + "</td></tr>")
            .collect(Collectors.joining()) + "</tbody></table></div>";
    }

    static String facts(List<Map.Entry<String, String>> rows) {
        return facts(rows, "");
    }

    static String idList(Collection<String> ids) {
        return "<ul class=\"ids\">" + ids.stream().map(id -> "<li>" + code(id) + "</li>")
            .collect(Collectors.joining()) + "</ul>";
    }

    static String list(Collection<String> entries) {
        return "<ul>" + entries.stream().map(entry -> "<li>" + escape(entry) + "</li>")
            .collect(Collectors.joining()) + "</ul>";
    }

    /**
     * A notice set apart from the text by an icon and a rule, where a reader looks first.
     *
     * @param kind {@code warning}, {@code ok} or {@code fail}
     * @param html the notice, already rendered
     * @return the admonition markup
     */
    static String admonition(String kind, String html) {
        return "<div class=\"admonitionblock " + kind + "\"><table><tbody><tr><td class=\"icon\">"
            + "<svg class=\"icon-" + kind + "\" viewBox=\"0 0 24 24\" aria-hidden=\"true\"><path fill=\"currentColor\" "
            + "d=\"" + ICONS.get(kind) + "\"/></svg></td><td class=\"content\">" + html
            + "</td></tr></tbody></table></div>";
    }
}
