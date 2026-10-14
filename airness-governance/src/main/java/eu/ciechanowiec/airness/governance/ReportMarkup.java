package eu.ciechanowiec.airness.governance;

import java.util.Collection;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.unbescape.html.HtmlEscape;

/**
 * Escaped text and common markup used by the report's sections.
 */
@UtilityClass
final class ReportMarkup {

    static String escape(String text) {
        return HtmlEscape.escapeHtml5(text);
    }

    static String paragraph(String text) {
        return "<p>" + escape(text) + "</p>";
    }

    static String cells(Collection<String> cells) {
        return cells.stream().map(cell -> "<td>" + escape(cell) + "</td>").collect(Collectors.joining());
    }

    static String table(Collection<String> headings, String rows) {
        String header = headings.stream().map(heading -> "<th>" + escape(heading) + "</th>")
            .collect(Collectors.joining());
        return "<div class=\"table-wrap\"><table class=\"tableblock\"><thead><tr>" + header
            + "</tr></thead><tbody>" + rows + "</tbody></table></div>";
    }

    static String list(Collection<String> entries) {
        return "<ul>" + entries.stream().map(entry -> "<li>" + escape(entry) + "</li>")
            .collect(Collectors.joining()) + "</ul>";
    }
}
