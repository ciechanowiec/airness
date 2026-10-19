package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The report's header: the draft notice, the verdict statements and the contents. The verdict precedes
 * the contents, so a reader who opens only the first page reads it. It states what was checked and
 * never calls the project compliant.
 *
 * @param report evaluated report
 */
record ReportVerdict(ComplianceReport report) {

    String header() {
        return "<div id=\"header\"><h1>Open Source Software Report</h1>" + this.preamble() + ReportOutline.contents()
            + "</div>";
    }

    private String preamble() {
        String scanned = this.report.evidence().scannedAt();
        return "<div id=\"preamble\">" + this.draftNotice() + this.verdict() + ReportMarkup.facts(
            List.of(
                Map.entry("Project", ReportMarkup.escape(this.report.project())),
                Map.entry("Source commit", ReportMarkup.code(this.report.source().commit())),
                Map.entry("Report generated", ReportMarkup.moment(this.report.source().generatedAt())),
                Map.entry(
                    "Vulnerability data as of", scanned.isEmpty() ? "No scan recorded" : ReportMarkup.moment(scanned)
                )
            )
        ) + "</div>";
    }

    private String draftNotice() {
        List<String> reasons = this.report.draftReasons();
        return reasons.isEmpty() ? "" : ReportMarkup.admonition(
            "warning", "<p>This report is a draft and must not be treated as release evidence. "
                + reasons.stream().map(ReportMarkup::escape).collect(Collectors.joining(" ")) + "</p>"
        );
    }

    private String verdict() {
        return "<ul class=\"statements verdict\">" + Stream.concat(
            Stream.of(this.licenseStatement()), this.vulnerabilityStatements()
        ).collect(Collectors.joining()) + "</ul>";
    }

    private String licenseStatement() {
        ReportInventory inventory = this.report.inventory();
        long outside = inventory.refused().size();
        return outside == 0
            ? statement(true, all(inventory.packages().size()) + " a license within the policy.")
            : statement(
                false, ReportCounts.plural(outside, "artifact version") + " "
                    + ReportCounts.agreeing(outside, "has", "have")
                    + " a license outside the policy. Each is listed in "
                    + ReportOutline.reference("review-outside") + "."
            );
    }

    // No statement reports recorded mitigations without a vulnerability: the scan fails on such an entry
    // before any report is written, so a written report never has one.
    private Stream<String> vulnerabilityStatements() {
        ReportInventory inventory = this.report.inventory();
        List<ReportAdvisory> open = inventory.open();
        List<ReportAdvisory> mitigated = inventory.mitigated();
        String openStatement = open.isEmpty()
            ? statement(true, noneOpen(mitigated))
            : statement(
                false, vulnerabilities(open) + " " + ReportCounts.agreeing(open.size(), "is", "are")
                    + " open, with no mitigation plan. Each is listed in " + ReportOutline.reference("advisories-all")
                    + "."
            );
        return mitigated.isEmpty() ? Stream.of(openStatement) : Stream.of(
            openStatement, statement(
                true, vulnerabilities(mitigated) + " "
                    + ReportCounts.agreeing(mitigated.size(), "is mitigated, with a", "are mitigated, each with a")
                    + " mitigation plan in " + ReportOutline.reference("advisories-plans") + "."
            )
        );
    }

    private static String noneOpen(List<ReportAdvisory> mitigated) {
        return mitigated.isEmpty() ? "No known vulnerabilities." : "No open vulnerabilities.";
    }

    // "2 vulnerabilities (1 high, 1 low)", most severe first, which is the order the advisories are listed in.
    private static String vulnerabilities(List<ReportAdvisory> advisories) {
        Map<String, Long> counts = advisories.stream().collect(
            Collectors.groupingBy(ReportVerdict::severity, Collectors.counting())
        );
        String breakdown = advisories.stream().map(ReportVerdict::severity).distinct()
            .map(severity -> counts.get(severity) + " " + ReportMarkup.escape(severity))
            .collect(Collectors.joining(", "));
        return ReportCounts.plural(advisories.size(), "vulnerability", "vulnerabilities") + " (" + breakdown + ")";
    }

    private static String severity(ReportAdvisory advisory) {
        return advisory.severity().toLowerCase(Locale.ROOT);
    }

    private static String all(int count) {
        return count == 1 ? "The 1 artifact version has" : "All " + count + " artifact versions have";
    }

    private static String statement(boolean passing, String text) {
        return "<li class=\"" + (passing ? "pass" : "fail") + "\">" + text + "</li>";
    }
}
