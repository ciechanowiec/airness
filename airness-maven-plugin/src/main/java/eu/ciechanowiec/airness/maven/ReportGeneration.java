package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ComplianceReport;
import eu.ciechanowiec.airness.governance.ComplianceReportHtml;
import eu.ciechanowiec.airness.governance.ReportEvidence;
import eu.ciechanowiec.airness.governance.ReportInventory;
import eu.ciechanowiec.airness.governance.ReportPackage;
import eu.ciechanowiec.airness.governance.ReportSource;
import eu.ciechanowiec.airness.governance.TreeFingerprint;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

/**
 * Assembles, prints, and publishes a report only while its source fingerprint still agrees.
 *
 * @param initial        source fingerprint captured before verification
 * @param project        destination and inherited policy
 * @param qualifications additional draft reasons
 */
record ReportGeneration(ReportSource initial, ReportProject project, List<String> qualifications) {

    ReportGeneration {
        qualifications = List.copyOf(qualifications);
    }

    static ReportGeneration from(MavenSession session, MavenProject owner) {
        return new ReportGeneration(
            ReportSession.source(session.getRepositorySession().getData()),
            ReportProject.from(ReportProjects.root(session.getAllProjects()), owner),
            ReportProjects.qualifications(
                ReportProjects.partial(session.getProjects(), session.getAllProjects()),
                session.getUserProperties().getProperty("airness.enforce", "true")
            )
        );
    }

    ReportGeneration reportOnly() {
        return new ReportGeneration(
            this.initial, this.project,
            Stream.concat(
                this.qualifications.stream(),
                Stream.of("Report-only invocation does not establish an Extended verification pass.")
            ).toList()
        );
    }

    ComplianceReport write(List<ReportModule> modules, Optional<String> destination) {
        Path root = this.project.root();
        ComplianceReport report = this.assemble(modules);
        unchanged(root, report.source());
        String html = ComplianceReportHtml.render(report);
        byte[] pdf = ReportPdf.print(html);
        unchanged(root, report.source());
        Path directory = destination.map(root::resolve)
            .orElseGet(
                () -> this.project.build().resolve("compliance")
            );
        new ReportPublication(root, directory, report.fileStem()).publish(html, pdf);
        unchanged(root, report.source());
        return report;
    }

    ComplianceReport assemble(List<ReportModule> modules) {
        List<ReportPackage> packages = ReportAggregation.packages(modules);
        ReportInventory inventory = new ReportInventory(
            packages,
            modules.stream().flatMap(module -> module.advisories().stream()).distinct().toList(),
            this.project.policy()
        );
        String sbom = ReportSbom.assemble(packages, modules);
        Map<String, String> provenance = Stream.concat(
            facts(modules, sbom).entrySet().stream(),
            this.project.identity().tools().entrySet().stream()
        ).collect(
            Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                Map.Entry::getValue
            )
        );
        ReportEvidence evidence = new ReportEvidence(sbom, "{}", provenance);
        String name = this.project.identity().name();
        ReportSource source = this.source();
        ComplianceReport assembled = new ComplianceReport(name, source, inventory, evidence);
        String downloads = ReportJson.encode(Map.of("sbom", sbom, "csv", assembled.csv()));
        return new ComplianceReport(name, source, inventory, new ReportEvidence(sbom, downloads, evidence.facts()));
    }

    private ReportSource source() {
        List<String> reasons = Stream.concat(this.initial.draftReasons().stream(), this.qualifications.stream())
            .toList();
        return new ReportSource(this.initial.commit(), this.initial.fingerprint(), Instant.now().toString(), reasons);
    }

    private static Map<String, String> facts(List<ReportModule> modules, String sbom) {
        Map<String, String> hashes = modules.stream().flatMap(
            module -> Stream.of(
                Map.entry(module.coordinate() + " SBOM SHA-256", module.evidence().get("SBOM SHA-256")),
                Map.entry(module.coordinate() + " scanner SHA-256", module.evidence().get("Scanner SHA-256")),
                Map.entry(module.coordinate() + " scan date", module.evidence().get("Scan date"))
            )
        ).collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        return Stream.concat(
            hashes.entrySet().stream(), Stream.of(
                Map.entry("Aggregate SBOM SHA-256", ReportJson.hash(sbom)),
                Map.entry("Modules", modules.stream().map(ReportModule::coordinate).collect(Collectors.joining(", ")))
            )
        ).collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static void unchanged(Path root, ReportSource source) {
        if (!source.fingerprint().equals(TreeFingerprint.from(root))) {
            throw new IllegalStateException("Compliance evidence does not describe the current tree; regenerate it");
        }
    }
}
