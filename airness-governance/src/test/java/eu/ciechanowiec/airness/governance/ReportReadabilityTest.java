package eu.ciechanowiec.airness.governance;

import static eu.ciechanowiec.airness.governance.ReportFixtures.COMMIT;
import static eu.ciechanowiec.airness.governance.ReportFixtures.DIRECT;
import static eu.ciechanowiec.airness.governance.ReportFixtures.MIT;
import static eu.ciechanowiec.airness.governance.ReportFixtures.POLICY;
import static eu.ciechanowiec.airness.governance.ReportFixtures.ROOT_MODULE;
import static eu.ciechanowiec.airness.governance.ReportFixtures.dependency;
import static eu.ciechanowiec.airness.governance.ReportFixtures.report;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The report reads for someone outside the build: it opens with a guide to its terms, names what it reads in plain
 * words, and never presents the build tool as an actor.
 */
class ReportReadabilityTest {

    private static final String IMAGE = "Dockerfile, on Alpine 3.24.4";
    private static final String MODULE = "group:module:pom:1";

    @Test
    void opensWithAGuideBetweenTheContentsAndTheFirstSection() {
        String html = ComplianceReportHtml.render(report(List.of(dependency(MIT)), List.of()));
        int guide = html.indexOf("<div id=\"guide\"><h2>How to read this report</h2>");
        assertTrue(guide > html.indexOf("id=\"toc\""), "the guide follows the contents");
        assertTrue(guide < html.indexOf("id=\"content\""), "the guide precedes the first section");
        assertTrue(html.contains("What the report does not cover"));
        assertTrue(html.contains("<li>Section 1 lists the known vulnerabilities"));
        for (String group : List.of("Lawyers", "managers", "Developers", "reviewers")) {
            assertFalse(html.contains(group), "the guide addresses no reader group");
        }
        assertTrue(html.contains("<td>Development, Test, Provided, Build</td>"));
        assertFalse(html.contains("&amp;#39;"), "an empty state is escaped once");
    }

    @Test
    void namesNoBuildToolAsAnActorOutsideTheRegenerationCommand() {
        ReportAdvisory advisory = new ReportAdvisory(
            "CVE-2026-0101", "HIGH", "pkg:maven/group/library@1?type=jar", List.of(ROOT_MODULE), "A flaw.",
            Optional.of(
                new ReportMitigation("Unrelated product.", "Remove it.", "2026-10-13", ReportMitigation.Kind.HARNESS)
            ),
            List.of()
        );
        String html = ComplianceReportHtml.render(report(List.of(dependency(MIT)), List.of(advisory)));
        assertTrue(html.contains("2026-10-13</span></span> in the shared build configuration"));
        String text = html.substring(html.indexOf("<body"), html.indexOf("<script type=\"application/json\""))
            .replace("airness:compliance-report", "").toLowerCase(Locale.ROOT);
        assertFalse(text.contains("airness"), "the report names the build tool");
        assertFalse(text.contains("harness"), "the report names the build harness");
    }

    @Test
    void labelsEachVulnerabilityDataDateByThePackagesItCovers() {
        ReportImagePackage system = new ReportImagePackage(IMAGE, "pkg:apk/alpine/zlib@1.3-r0", List.of("Zlib"), true);
        String images = ComplianceReportHtml.render(withImages(List.of(), system));
        assertTrue(images.contains("<td>Vulnerability data for container image packages as of</td>"));
        assertTrue(images.contains("No vulnerability in a container image is waiting for a fix."));
        ReportPackage npm = new ReportPackage(
            "pkg:npm/kit@1.0.0", List.of(MIT), List.of(new ReportUse(ROOT_MODULE, ReportUse.PRODUCTION, DIRECT)),
            Map.of()
        );
        String both = ComplianceReportHtml.render(withImages(List.of(npm), system));
        assertTrue(
            both.contains("<td>Vulnerability data for JavaScript (npm) and container image packages as of</td>")
        );
    }

    @Test
    void namesEveryProvenanceValueInPlainWords() {
        ComplianceReport base = report(List.of(dependency(MIT)), List.of());
        ComplianceReport report = new ComplianceReport(
            base.project(), new ReportSource(
                COMMIT, "fingerprint", "2026-10-13T12:00:00Z", List.of(ReportSource.DIRTY)
            ),
            base.inventory(), new ReportEvidence(
                "{}", base.evidence().downloads(), Map.of(
                    "Aggregate SBOM SHA-256", "digest", MODULE + " SBOM SHA-256", "digest", MODULE + " scanner SHA-256",
                    "digest", MODULE + " scan date", "2026-10-13T11:58:00Z", "Modules", MODULE,
                    "Vulnerability scanner", "OWASP Dependency-Check 13.0.0", "Build checks version", "1.0.14-SNAPSHOT"
                )
            )
        );
        String html = ComplianceReportHtml.render(report);
        assertTrue(html.contains("<td>Fingerprint of the SBOM embedded in the HTML edition (SHA-256)</td>"));
        assertTrue(html.contains("<td>" + MODULE + ": fingerprint of its SBOM (SHA-256)</td>"));
        assertTrue(html.contains("<td>" + MODULE + ": fingerprint of its vulnerability scan results (SHA-256)</td>"));
        assertTrue(html.contains("<td>" + MODULE + ": vulnerability scan date</td>"));
        assertTrue(html.contains("<td>Modules covered</td>"));
        assertTrue(html.contains("<td>Vulnerability scanner for Java (Maven) packages</td>"));
        assertTrue(html.contains("<td>Build checks version</td><td>1.0.14-SNAPSHOT (an unreleased development"));
        assertFalse(html.contains("&amp;#39;"), "no text is escaped twice");
        assertTrue(html.contains("can&#39;t be reproduced exactly from that commit"));
        assertFalse(html.contains("2026-10-13T11:58:00Z"), "a scan date reads as a date, not as a timestamp");
    }

    private static ComplianceReport withImages(List<ReportPackage> packages, ReportImagePackage image) {
        ComplianceReport base = report(packages, List.of());
        return new ComplianceReport(
            base.project(), base.source(), new ReportInventory(packages, List.of(), POLICY, List.of(image)),
            base.evidence()
        );
    }
}
