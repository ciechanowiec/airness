package eu.ciechanowiec.airness.governance;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The report's narrative, summaries, notice texts, and provenance.
 *
 * @param report evaluated report
 */
record ReportSections(ComplianceReport report) {

    private static final int PERCENT = 100;
    private static final List<String> SEVERITIES = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");

    String opening() {
        String verdict = this.report.inventory().compliant() ? "Compliant" : "Not compliant";
        return ReportMarkup.paragraph("Project: " + this.report.project())
            + "<p>Dependency policy verdict: <strong>" + verdict + "</strong>.</p>"
            + ReportMarkup.paragraph("Generated " + this.report.source().generatedAt())
            + (this.report.draftReasons().isEmpty() ? "" : "<aside class=\"draft-notice\"><strong>Draft report</strong>"
                + ReportMarkup.list(this.report.draftReasons()) + "</aside>");
    }

    String summary() {
        ReportInventory inventory = this.report.inventory();
        String figures = figure(inventory.packages().size(), "Resolved artifact versions")
            + figure(inventory.packages().stream().filter(ReportPackage::production).count(), "Production artifacts")
            + figure(
                inventory.packages().stream().filter(entry -> !entry.production()).count(), "Development and build"
            )
            + figure(
                inventory.packages().stream().filter(
                    entry -> "Weak copyleft".equals(
                        inventory.policy().classification(entry.licenses())
                    )
                ).count(), "Weak copyleft artifacts"
            )
            + figure(
                inventory.packages().stream().filter(inventory.policy()::refuses).count(),
                "Outside license policy"
            )
            + figure(
                inventory.advisories().stream().filter(advisory -> "Unresolved".equals(advisory.status())).count(),
                "Unresolved advisories"
            );
        return ReportMarkup.paragraph(
            "The figures below use the resolved dependencies and the evidence judged by "
                + "the build. The verdict covers dependency policy; it is not a verdict on every Airness check."
        )
            + "<div class=\"figures\">" + figures + "</div>" + this.licenseChart() + this.commonLicenses()
            + this.severityChart();
    }

    String policy() {
        ReportLicensePolicy policy = this.report.inventory().policy();
        List<String> permitted = policy.allowed().stream().sorted().toList();
        return ReportMarkup.paragraph(
            "This is the allowlist the inherited license check enforces. A supplier's "
                + "declared spelling is normalized only by the same aliases that check reads. Unknown licenses fail."
        )
            + ReportMarkup.list(permitted)
            + ReportMarkup.paragraph(
                "Copyleft classification identifies dependencies for review. The report "
                    + "records declarations and shipped notices; it does not establish how a product is distributed."
            ) + ReportTables.licenseEvidence(this.report.inventory());
    }

    String notices() {
        List<ReportPackage> production = this.report.inventory().packages().stream()
            .filter(ReportPackage::production).toList();
        List<String> missing = production.stream().filter(entry -> entry.archiveNotices().isEmpty())
            .map(ReportPackage::coordinate).toList();
        Map<String, List<String>> grouped = production.stream()
            .flatMap(
                entry -> entry.archiveNotices().entrySet().stream()
                    .map(notice -> Map.entry(notice.getValue(), entry.coordinate() + " / " + notice.getKey()))
            ).collect(
                Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList()))
            );
        return ReportMarkup.paragraph(
            "These are the license and notice texts found in production dependency "
                + "archives. Identical texts are grouped with every artifact and archive entry that carries them."
        )
            + "<h3>Packages without a license file</h3>"
            + (missing.isEmpty() ? ReportMarkup.paragraph(
                "Every production artifact supplies a license or notice file."
            )
                : ReportMarkup.list(missing))
            + grouped.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(ReportSections::notice)
                .collect(Collectors.joining()) + ReportAssets.credits();
    }

    String provenance() {
        ReportSource source = this.report.source();
        Map<String, String> facts = Map.of(
            "Source commit", source.commit(), "Source fingerprint (SHA-256)",
            source.fingerprint(), "Report date", source.generatedAt(), "Regenerate",
            "mvn -Pextended airness:compliance-report"
        );
        return facts(facts) + facts(this.report.evidence().facts())
            + ReportMarkup.paragraph(
                "The aggregate SBOM covers the resolved Maven inputs listed here. "
                    + "The separately published per-module SBOMs retain their production dependency scope. "
                    + "Container operating-system packages and arbitrary distribution files "
                    + "are outside this inventory. "
                    + "Vulnerability findings describe the database available at the recorded scan time."
            );
    }

    private String licenseChart() {
        ReportInventory inventory = this.report.inventory();
        Map<String, Long> counts = inventory.packages().stream().collect(
            Collectors.groupingBy(
                entry -> inventory.policy().classification(entry.licenses()), Collectors.counting()
            )
        );
        return "<h3>License classes</h3>" + chart(counts);
    }

    private String severityChart() {
        Map<String, Long> counts = this.report.inventory().advisories().stream()
            .collect(Collectors.groupingBy(ReportAdvisory::severity, Collectors.counting()));
        Map<String, Long> complete = Stream.concat(
            counts.entrySet().stream(),
            SEVERITIES.stream().map(severity -> Map.entry(severity, 0L))
        ).collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue, Long::sum));
        return "<h3>Advisories by severity</h3>" + chart(
            complete,
            Comparator.comparingInt(entry -> SEVERITIES.indexOf(entry.getKey()))
        );
    }

    private String commonLicenses() {
        Map<String, Long> counts = this.report.inventory().packages().stream().collect(
            Collectors.groupingBy(
                ReportPackage::licenseSummary, Collectors.counting()
            )
        );
        return "<h3>Declared licenses</h3>" + chart(counts);
    }

    private static String chart(Map<String, Long> counts) {
        return chart(counts, Map.Entry.comparingByKey());
    }

    private static String chart(Map<String, Long> counts, Comparator<Map.Entry<String, Long>> order) {
        long maximum = Math.max(1, counts.values().stream().mapToLong(Long::longValue).max().orElse(1));
        return "<div class=\"bars\">" + counts.entrySet().stream().sorted(order)
            .map(entry -> bar(entry, maximum)).collect(Collectors.joining()) + "</div>";
    }

    private static String bar(Map.Entry<String, Long> entry, long maximum) {
        long width = entry.getValue() * PERCENT / maximum;
        return "<span class=\"label\">" + ReportMarkup.escape(entry.getKey())
            + "</span><span class=\"track\"><span class=\"fill fill-permissive\" style=\"width:" + width
            + "%\"></span></span><span class=\"count\">" + entry.getValue() + "</span>";
    }

    private static String figure(long count, String label) {
        return "<div class=\"figure\"><b>" + count + "</b><span>" + ReportMarkup.escape(label) + "</span></div>";
    }

    private static String notice(Map.Entry<String, List<String>> entry) {
        return "<article class=\"notice\"><h3>License and notice text</h3>"
            + ReportMarkup.list(entry.getValue().stream().sorted().toList())
            + "<div class=\"license-text\">" + entry.getKey().lines().map(ReportMarkup::paragraph)
                .collect(Collectors.joining()) + "</div></article>";
    }

    private static String facts(Map<String, String> facts) {
        return ReportMarkup.table(
            List.of("Evidence", "Value"), facts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "<tr>" + ReportMarkup.cells(List.of(entry.getKey(), entry.getValue())) + "</tr>")
                .collect(Collectors.joining())
        );
    }
}
