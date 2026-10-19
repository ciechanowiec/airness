package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * The package inventory and the dependency rows other sections review, rendered from their evaluated
 * evidence.
 */
@UtilityClass
final class ReportTables {

    private static final String CONTROLS = """
        <div class="controls"><input id="filter-text" type="search" aria-label="Filter by artifact, license or module"
        placeholder="Filter by artifact, license or module">
        <select id="filter-scope" aria-label="Scope"><option value="">All scopes</option>
        <option value="production">Production</option><option value="provided">Provided</option>
        <option value="test">Test</option><option value="build">Build tools</option></select>
        <select id="filter-class" aria-label="License class"><option value="">All license classes</option>
        <option value="permissive">Permissive</option><option value="weak-copyleft">Weak copyleft</option>
        <option value="refused">Outside policy</option></select>
        <output id="filter-count" aria-live="polite"></output></div>
        """;
    private static final List<String> HEADINGS = List.of("Artifact", "License", "Class", "Module / scope / relation");

    static String inventory(ComplianceReport report) {
        ReportInventory inventory = report.inventory();
        String rows = inventory.packages().stream().map(entry -> row(inventory.policy(), entry))
            .collect(Collectors.joining());
        return ReportOutline.section(
            "inventory", ReportMarkup.paragraphs(
                List.of(
                    "This section lists every resolved artifact version, "
                        + ReportCounts.plural(inventory.packages().size(), "row")
                        + ", with the license its published metadata declares.",
                    "Each row names every module that resolves the version, with its Maven scope, or build for a "
                        + "plugin input, and whether the module declares it directly or reaches it transitively."
                )
            ) + downloads(report.fileStem()) + CONTROLS
                + "<div class=\"table-wrap\"><table class=\"tableblock\" id=\"inventory-table\"><thead><tr>"
                + HEADINGS.stream().map(heading -> "<th scope=\"col\" data-sort>" + heading + "</th>")
                    .collect(Collectors.joining())
                + "</tr></thead><tbody>" + rows + "</tbody></table></div>"
        );
    }

    static String review(ReportPackage entry) {
        return ReportMarkup.row(
            List.of(ReportMarkup.escape(entry.coordinate()), ReportMarkup.code(entry.licenseSummary()), uses(entry))
        );
    }

    static String licenseEvidence(ReportInventory inventory) {
        String entries = inventory.packages().stream().flatMap(
            entry -> entry.notices().entrySet().stream()
                .filter(notice -> notice.getKey().startsWith(ReportPackage.LICENSE_EVIDENCE_PREFIX))
                .map(notice -> evidence(entry.coordinate(), notice))
        ).collect(Collectors.joining());
        return entries.isEmpty() ? "" : ReportMarkup.paragraphs(
            List.of(
                "Airness verified these exact artifact bytes against upstream release evidence. Missing POM licenses "
                    + "are supplemented only for the recorded version and checksum. The inherited allowlist still "
                    + "decides whether the license is admitted."
            )
        ) + entries;
    }

    private static String evidence(String coordinate, Map.Entry<String, String> notice) {
        String source = notice.getKey().substring(ReportPackage.LICENSE_EVIDENCE_PREFIX.length());
        String link = source.startsWith("https://")
            ? "<a href=\"" + ReportMarkup.escape(source) + "\" rel=\"noreferrer noopener\">Upstream release "
                + "evidence</a>"
            : ReportMarkup.escape(source);
        return "<div class=\"notice\"><p class=\"title\">" + ReportMarkup.escape(coordinate) + "</p>"
            + "<p class=\"notice-packages\">" + link + "</p><div class=\"license-text\">"
            + ReportNoticeText.render(ReportNoticeText.reflow(notice.getValue())) + "</div></div>";
    }

    private static String row(ReportLicensePolicy policy, ReportPackage entry) {
        String classification = policy.classification(entry.licenses());
        String scope = entry.uses().stream().map(
            use -> List.of("compile", "runtime").contains(use.scope())
                ? "production" : use.scope()
        ).distinct().sorted().collect(Collectors.joining(" "));
        return "<tr data-scope=\"" + ReportMarkup.escape(scope) + "\" data-class=\""
            + ReportChips.classKey(classification) + "\">" + "<td>" + ReportMarkup.escape(entry.coordinate())
            + "</td><td>" + ReportMarkup.code(entry.licenseSummary()) + "</td><td>"
            + ReportChips.classChip(classification) + "</td><td>" + uses(entry) + "</td></tr>";
    }

    private static String uses(ReportPackage entry) {
        return ReportMarkup.escape(
            entry.uses().stream().map(use -> use.module() + " / " + use.scope() + " / " + use.relation())
                .collect(Collectors.joining("; "))
        );
    }

    private static String downloads(String stem) {
        String safe = ReportMarkup.escape(stem);
        return "<div class=\"downloads\"><button type=\"button\" hidden data-download=\"sbom\" data-file=\"" + safe
            + ".cdx.json\">Download SBOM (CycloneDX JSON)</button>"
            + "<button type=\"button\" hidden data-download=\"csv\" data-file=\"" + safe
            + ".csv\">Download inventory (CSV)</button></div>";
    }
}
