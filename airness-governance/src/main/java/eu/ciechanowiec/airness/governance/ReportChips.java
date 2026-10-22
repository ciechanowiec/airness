package eu.ciechanowiec.airness.governance;

import java.util.Locale;
import java.util.Map;
import lombok.experimental.UtilityClass;

/**
 * The small labelled chips the report marks a severity or a license class with.
 */
@UtilityClass
final class ReportChips {

    static String severityChip(String severity) {
        String level = ReportMarkup.escape(severity.toLowerCase(Locale.ROOT));
        return "<span class=\"sev sev-" + level + "\">" + level + "</span>";
    }

    static String classChip(String classification) {
        return "<span class=\"chip chip-" + classKey(classification) + "\">" + ReportMarkup.escape(classification)
            + "</span>";
    }

    /**
     * The token a license class is styled and filtered by.
     *
     * @param classification the readable class
     * @return {@code permissive}, {@code weak-copyleft} or {@code refused}
     */
    static String classKey(String classification) {
        return Map.of("Permissive", "permissive", "Weak copyleft", "weak-copyleft")
            .getOrDefault(classification, "refused");
    }
}
