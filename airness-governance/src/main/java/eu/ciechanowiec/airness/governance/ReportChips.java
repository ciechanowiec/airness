package eu.ciechanowiec.airness.governance;

import java.util.Locale;
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

    static String classChip(LicenseClass classification) {
        return "<span class=\"chip chip-" + classification.key() + "\">" + ReportMarkup.escape(classification.label())
            + "</span>";
    }
}
