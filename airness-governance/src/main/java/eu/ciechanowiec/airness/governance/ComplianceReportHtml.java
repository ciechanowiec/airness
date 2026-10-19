package eu.ciechanowiec.airness.governance;

import lombok.experimental.UtilityClass;

/**
 * Renders one self-contained, offline-readable open source software report: no external asset, so the
 * file is the whole deliverable whether it is opened offline, attached to a message, or printed to PDF.
 */
@UtilityClass
public final class ComplianceReportHtml {

    private static final int SHORT_COMMIT = 12;

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
            + "</title><style>" + ReportAssets.styles() + "</style></head><body class=\"article toc2 toc-left\">"
            + new ReportVerdict(report).header() + "<main id=\"content\">"
            + new ReportVulnerabilitySection(report).render() + licenses.licenses() + licenses.policy()
            + ReportTables.inventory(report) + sections.notices() + sections.provenance()
            + "</main><footer id=\"footer\">Report for " + ReportMarkup.escape(report.project()) + " at commit "
            + ReportMarkup.code(commit.substring(0, Math.min(SHORT_COMMIT, commit.length()))) + ", "
            + ReportMarkup.moment(report.source().generatedAt()) + ".</footer>"
            + "<script type=\"application/json\" id=\"report-data\">" + report.evidence().downloads()
            + "</script><script>" + ReportAssets.script() + "</script></body></html>\n";
    }
}
