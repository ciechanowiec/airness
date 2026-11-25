package eu.ciechanowiec.airness.governance;

import lombok.experimental.UtilityClass;

/**
 * The small labelled chips the report marks a severity or a license class with.
 */
@UtilityClass
final class ReportChips {

    static String severityChip(ReportSeverity severity) {
        return "<span class=\"sev sev-" + severity.key() + "\">" + severity.label() + "</span>";
    }

    static String classChip(LicenseClass classification) {
        return "<span class=\"chip chip-" + classification.key() + "\">" + ReportMarkup.escape(classification.label())
            + "</span>";
    }
}
