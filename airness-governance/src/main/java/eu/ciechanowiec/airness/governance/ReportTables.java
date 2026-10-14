package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * Dependency and advisory tables rendered from their evaluated evidence.
 */
@UtilityClass
final class ReportTables {

    private static final String CONTROLS = """
        <div class="controls"><input id="filter-text" type="search" aria-label="Search inventory"
        placeholder="Search packages, licenses, or modules">
        <select id="filter-scope" aria-label="Dependency use"><option value="">All uses</option>
        <option value="production">Production</option><option value="provided">Provided</option>
        <option value="test">Test</option><option value="build">Build tools</option></select>
        <select id="filter-class" aria-label="License class"><option value="">All classes</option>
        <option>Permissive</option><option>Weak copyleft</option><option>Outside policy</option></select>
        <span id="filter-count" aria-live="polite"></span></div>
        """;

    static String inventory(ComplianceReport report) {
        String rows = report.inventory().packages().stream().map(entry -> row(report.inventory().policy(), entry))
            .collect(Collectors.joining());
        String table = ReportMarkup.table(List.of("Artifact", "License", "Class", "Module / scope / relation"), rows)
            .replace("<table class=", "<table id=\"inventory-table\" class=")
            .replace("<th>", "<th data-sort=\"text\">");
        return downloads(report.fileStem()) + CONTROLS + table;
    }

    static String review(ReportInventory inventory) {
        String rows = inventory.packages().stream()
            .filter(entry -> !"Permissive".equals(inventory.policy().classification(entry.licenses())))
            .map(entry -> row(inventory.policy(), entry)).collect(Collectors.joining());
        return rows.isEmpty() ? ReportMarkup.paragraph("No dependency requires copyleft or license-policy review.")
            : ReportMarkup.table(List.of("Artifact", "License", "Class", "Module / scope / relation"), rows);
    }

    static String advisories(List<ReportAdvisory> advisories) {
        return advisories.isEmpty() ? ReportMarkup.paragraph("The scanner reported no known vulnerabilities.")
            : ReportMarkup.table(
                List.of("Advisory", "Severity", "Disposition", "Evidence"), advisories.stream()
                    .map(ReportTables::advisory).collect(Collectors.joining())
            );
    }

    private static String advisory(ReportAdvisory advisory) {
        String evidence = advisory.evidence().entrySet().stream().sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + ": " + entry.getValue()).collect(Collectors.joining("\n"));
        return "<tr>" + ReportMarkup.cells(
            List.of(advisory.identity(), advisory.severity(), advisory.status(), evidence)
        )
            + "</tr>";
    }

    private static String row(ReportLicensePolicy policy, ReportPackage entry) {
        String classification = policy.classification(entry.licenses());
        String uses = entry.uses().stream().map(use -> use.module() + " / " + use.scope() + " / " + use.relation())
            .collect(Collectors.joining("; "));
        String scope = entry.uses().stream().map(
            use -> List.of("compile", "runtime").contains(use.scope())
                ? "production" : use.scope()
        ).distinct().sorted().collect(Collectors.joining(" "));
        return "<tr data-scope=\"" + ReportMarkup.escape(scope) + "\" data-class=\"" + classification + "\">"
            + ReportMarkup.cells(
                List.of(entry.coordinate(), entry.licenseSummary(), classification, uses)
            )
            + "</tr>";
    }

    private static String downloads(String stem) {
        String safe = ReportMarkup.escape(stem);
        return "<div class=\"downloads\"><button hidden data-download=\"sbom\" data-file=\"" + safe
            + ".cdx.json\">Download SBOM (CycloneDX JSON)</button>"
            + "<button hidden data-download=\"csv\" data-file=\"" + safe
            + ".csv\">Download inventory (CSV)</button></div>";
    }
}
