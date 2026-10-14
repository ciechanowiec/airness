package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ComplianceReportTest {

    private static final String MIT = "MIT";
    private static final String DIRECT = "Direct";
    private static final String COMMIT = "a12345678901234567890123456789012345678901";
    private static final ReportLicensePolicy POLICY = new ReportLicensePolicy(
        Set.of(MIT, "MPL-2.0"),
        Map.of("MIT License", MIT)
    );

    @Test
    void classifiesOnlyAliasesTheBuildActuallyDeclares() {
        assertEquals("Permissive", POLICY.classification(List.of("MIT License")));
        assertEquals("Weak copyleft", POLICY.classification(List.of("MPL-2.0")));
        assertEquals("Outside policy", POLICY.classification(List.of("Proprietary")));
        assertEquals("Outside policy", POLICY.classification(List.of()));
        assertEquals("Permissive", POLICY.classification(List.of("Proprietary", MIT)));
        assertThrows(IllegalArgumentException.class, () -> new ReportLicensePolicy(Set.of(), Map.of()));
    }

    @Test
    void keepsDraftStatusSeparateFromTheDependencyVerdict() {
        ComplianceReport report = report(List.of(dependency(MIT)), List.of());
        assertTrue(report.inventory().compliant());
        assertEquals(List.of("Uncommitted sources"), report.draftReasons());
        assertEquals("oss-compliance-report-sample-a12345678901", report.fileStem());
    }

    @Test
    void reportsPolicyFailuresWithoutLosingSuppressedFindings() {
        ReportAdvisory excused = new ReportAdvisory(
            "CVE-2026-1234", "HIGH", "Suppressed",
            Map.of("Exception", "2026-10-13: unused feature")
        );
        assertTrue(report(List.of(dependency(MIT)), List.of(excused)).inventory().compliant());
        ReportAdvisory unresolved = new ReportAdvisory("CVE-2026-1235", "HIGH", "Unresolved", Map.of());
        assertFalse(report(List.of(dependency(MIT)), List.of(unresolved)).inventory().compliant());
        assertTrue(
            report(List.of(dependency(MIT)), List.of(unresolved)).inventory().failures().getFirst()
                .contains(unresolved.identity())
        );
        assertTrue(
            report(List.of(dependency("Unknown")), List.of()).inventory().failures().getFirst()
                .contains("group:library:jar:1")
        );
        assertTrue(
            report(List.of(dependency("Unknown")), List.of()).draftReasons().stream()
                .anyMatch(reason -> reason.contains("do not satisfy"))
        );
    }

    @Test
    void exportsEachModuleUseAndEscapesCsvCells() {
        ReportPackage entry = new ReportPackage(
            "=formula", List.of(MIT),
            List.of(
                new ReportUse("module,one", "compile", DIRECT), new ReportUse("module\"two", "test", "Transitive")
            ),
            Map.of()
        );
        String csv = report(List.of(entry), List.of()).csv();
        assertTrue(csv.contains("\"'=formula\""));
        assertTrue(csv.contains("\"module,one\",\"compile\",\"Direct\""));
        assertTrue(csv.contains("\"module\"\"two\",\"test\",\"Transitive\""));
    }

    @Test
    void rendersEvidenceAndNoticesWithoutExecutingTheirMarkup() {
        ReportPackage entry = new ReportPackage(
            "group:library:jar:1", List.of("MIT License"),
            List.of(
                new ReportUse("root", "runtime", DIRECT),
                new ReportUse("other", "test\" onmouseover=\"alert(1)", "Transitive")
            ), Map.of("META-INF/LICENSE", "<script>alert(1)</script>")
        );
        String html = ComplianceReportHtml.render(report(List.of(entry, dependency("MPL-2.0")), List.of()));
        assertTrue(html.contains("data:font/woff2;base64,"));
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertTrue(html.contains("inventory-table"));
        assertTrue(html.contains("Weak copyleft"));
        assertTrue(html.contains(COMMIT));
        assertFalse(html.contains("<script>alert(1)</script>"));
        assertFalse(html.contains("<script src="));
        assertFalse(html.contains("\" onmouseover=\""));
    }

    @Test
    void rendersAnEmptyInventoryAndUnknownAdvisorySeverityExplicitly() {
        String empty = ComplianceReportHtml.render(report(List.of(), List.of()));
        assertTrue(empty.contains("No dependency requires copyleft"));
        assertTrue(empty.contains("no known vulnerabilities"));
        ReportAdvisory advisory = new ReportAdvisory(
            "ADVISORY-1", "Unrated", "Unresolved",
            Map.of("Artifact", "group:unsafe:1")
        );
        ReportPackage missing = new ReportPackage(
            "group:library:jar:1", List.of(), List.of(new ReportUse("root", "compile", DIRECT)), Map.of()
        );
        String failing = ComplianceReportHtml.render(report(List.of(missing), List.of(advisory)));
        assertTrue(failing.contains("Not declared"));
        assertTrue(failing.contains("Not compliant"));
        assertTrue(failing.contains("Unrated"));
        assertTrue(failing.contains("group:unsafe:1"));
    }

    @Test
    void rendersCleanReportsWithSeparateDevelopmentTotals() {
        ReportPackage entry = new ReportPackage(
            "group:test:jar:1", List.of(MIT), List.of(new ReportUse("root", "test", DIRECT)), Map.of()
        );
        ComplianceReport original = report(List.of(entry), List.of());
        ComplianceReport clean = new ComplianceReport(
            original.project(), new ReportSource(COMMIT, "fingerprint", "now", List.of()),
            original.inventory(), original.evidence()
        );
        String html = ComplianceReportHtml.render(clean);
        assertFalse(html.contains("Draft report"));
        assertTrue(html.contains("<b>0</b><span>Production artifacts</span>"));
        assertTrue(html.contains("<b>1</b><span>Development and build</span>"));
    }

    @Test
    void refusesEmbeddedDownloadDataThatCanCloseItsScriptElement() {
        assertThrows(IllegalArgumentException.class, () -> new ReportEvidence("{}", "</ScRiPt>", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> ReportOutline.section("Undeclared", "body"));
    }

    @Test
    void doesNotTreatProvidedOrTestArtifactsAsShippedProductionNotices() {
        ReportPackage entry = new ReportPackage(
            "group:test:jar:1", List.of(MIT),
            List.of(new ReportUse("root", "provided", DIRECT), new ReportUse("root", "test", DIRECT)), Map.of()
        );
        assertFalse(entry.production());
        assertTrue(dependency(MIT).production());
    }

    private static ReportPackage dependency(String license) {
        return new ReportPackage(
            "group:library:jar:1", List.of(license),
            List.of(new ReportUse("root", "compile", DIRECT)), Map.of()
        );
    }

    private static ComplianceReport report(List<ReportPackage> packages, List<ReportAdvisory> advisories) {
        return new ComplianceReport(
            "sample", new ReportSource(
                COMMIT, "fingerprint", "2026-10-13T12:00:00Z",
                List.of("Uncommitted sources")
            ), new ReportInventory(packages, advisories, POLICY),
            new ReportEvidence("{}", "{\"sbom\":\"{}\",\"csv\":\"header\"}", Map.of("SBOM SHA-256", "digest"))
        );
    }
}
