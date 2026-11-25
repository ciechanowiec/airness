package eu.ciechanowiec.airness.governance;

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Renders one self-contained, offline-readable open source software report: no external asset, so the
 * file is the whole deliverable whether it is opened offline, attached to a message, or printed to PDF.
 */
@UtilityClass
public final class ComplianceReportHtml {

    private static final int SHORT_COMMIT = 12;
    // What would end the CSS string the header is written in, or the style element that holds it.
    private static final Pattern UNSAFE_IN_STYLE = Pattern.compile("[\\p{Cntrl}\"\\\\<>]");

    /**
     * Renders the screen and print document from evaluated evidence.
     *
     * @param report the complete report
     * @return HTML with inline resources and safely encoded downloads
     */
    public static String render(ComplianceReport report) {
        ReportSections sections = new ReportSections(report);
        ReportLicenseSections licenses = new ReportLicenseSections(report);
        String commit = report.source().commit();
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
            + "<title>Open source software report - " + ReportMarkup.escape(report.project())
            + "</title><style>" + ReportAssets.styles() + running(report) + "</style></head>"
            + "<body class=\"article toc2 toc-left\">"
            + new ReportVerdict(report).header() + "<main id=\"content\">"
            + new ReportVulnerabilitySection(report).render() + licenses.licenses() + licenses.policy()
            + ReportTables.inventory(report) + sections.notices() + sections.provenance()
            + "</main><footer id=\"footer\">Report for " + ReportMarkup.escape(report.project()) + " at commit "
            + ReportMarkup.code(commit.substring(0, Math.min(SHORT_COMMIT, commit.length()))) + ", "
            + ReportMarkup.moment(report.source().generatedAt()) + ".</footer>"
            + "<script type=\"application/json\" id=\"report-data\">" + report.evidence().downloads()
            + "</script><script>" + ReportAssets.script() + "</script></body></html>\n";
    }

    // The running header of every printed page after the first names the project and the commit, so a page read on
    // its own still says which report it belongs to.
    private static String running(ComplianceReport report) {
        String commit = report.source().commit();
        String text = "Open source software report | " + report.project() + " | "
            + commit.substring(0, Math.min(SHORT_COMMIT, commit.length()));
        String safe = UNSAFE_IN_STYLE.matcher(text).replaceAll("");
        return "@page{@top-right{content:\"" + safe + "\";font:8pt \"Source Serif 4\",Georgia,serif;color:#4d5560;"
            + "vertical-align:bottom;padding-bottom:3mm}}@page:first{@top-right{content:none}}";
    }
}
