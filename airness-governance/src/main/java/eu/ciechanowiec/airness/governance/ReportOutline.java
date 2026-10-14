package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;

/**
 * The single numbered outline shared by headings, navigation, and printed contents.
 */
@UtilityClass
final class ReportOutline {

    private static final List<String> SECTIONS = List.of(
        "Executive summary", "License policy", "Copyleft and policy review", "Known vulnerabilities",
        "Package inventory", "Third-party notices", "Provenance and reproduction"
    );

    static String contents() {
        return "<nav id=\"toc\" aria-labelledby=\"toctitle\"><div id=\"toctitle\">Table of Contents</div>"
            + "<ul class=\"sectlevel1\">" + IntStream.range(0, SECTIONS.size()).mapToObj(ReportOutline::link)
                .collect(Collectors.joining()) + "</ul></nav>";
    }

    static String section(String title, String content) {
        int number = SECTIONS.indexOf(title) + 1;
        if (number == 0) {
            throw new IllegalArgumentException("Unknown report section: " + title);
        }
        return "<section class=\"sect1\"><h2 id=\"section-" + number + "\">"
            + heading(number, title) + "</h2>" + content + "</section>";
    }

    private static String link(int index) {
        int number = index + 1;
        return "<li><a href=\"#section-" + number + "\">" + heading(number, SECTIONS.get(index))
            + "<span class=\"leader\"></span><span class=\"page\" data-for=\"section-" + number
            + "\">000</span></a></li>";
    }

    private static String heading(int number, String title) {
        return "<span class=\"adnum\">" + number + ".</span><span class=\"adtext\">" + title + "</span>";
    }
}
