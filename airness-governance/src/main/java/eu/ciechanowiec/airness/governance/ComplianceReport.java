package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A complete dependency report assembled from the evidence used by the build.
 *
 * @param project   root project identity
 * @param source    source identity and draft reasons
 * @param inventory evaluated dependencies
 * @param evidence  embedded evidence and provenance
 */
public record ComplianceReport(
    String project, ReportSource source, ReportInventory inventory,
    ReportEvidence evidence
) {

    private static final int SHORT_COMMIT = 12;

    /**
     * The portable filename shared by the HTML and PDF outputs.
     *
     * @return filename without extension
     */
    public String fileStem() {
        String name = this.project.replaceAll("[^A-Za-z0-9._-]", "-");
        return "oss-compliance-report-" + name + "-" + this.source.commit().substring(0, SHORT_COMMIT);
    }

    /**
     * All qualifications of this report's release status.
     *
     * @return source qualifications followed by any policy failure
     */
    public List<String> draftReasons() {
        Stream<String> policy = this.inventory.compliant() ? Stream.empty()
            : Stream.of("The dependencies do not satisfy the policy this report states.");
        return Stream.concat(this.source.draftReasons().stream(), policy).toList();
    }

    /**
     * Exports the inventory without losing module-level scope and relation.
     *
     * @return RFC 4180 CSV with a header
     */
    public String csv() {
        return "Artifact,License,Class,Module,Scope,Relation\r\n" + this.inventory.packages().stream()
            .flatMap(entry -> entry.uses().stream().map(use -> this.csv(entry, use)))
            .collect(Collectors.joining());
    }

    private String csv(ReportPackage entry, ReportUse use) {
        return Stream.of(
            entry.coordinate(), entry.licenseSummary(),
            this.inventory.policy().classification(entry.licenses()), use.module(), use.scope(), use.relation()
        ).map(ComplianceReport::csvCell).collect(Collectors.joining(",")) + "\r\n";
    }

    private static String csvCell(String text) {
        String beginning = text.stripLeading();
        boolean formula = Stream.of("=", "+", "@", "-").anyMatch(beginning::startsWith);
        String safe = formula ? "'" + text : text;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
