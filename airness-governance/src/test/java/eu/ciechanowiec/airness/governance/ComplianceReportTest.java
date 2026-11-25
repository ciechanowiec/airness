package eu.ciechanowiec.airness.governance;

import static eu.ciechanowiec.airness.governance.ReportFixtures.COMMIT;
import static eu.ciechanowiec.airness.governance.ReportFixtures.DIRECT;
import static eu.ciechanowiec.airness.governance.ReportFixtures.MIT;
import static eu.ciechanowiec.airness.governance.ReportFixtures.POLICY;
import static eu.ciechanowiec.airness.governance.ReportFixtures.ROOT_MODULE;
import static eu.ciechanowiec.airness.governance.ReportFixtures.dependency;
import static eu.ciechanowiec.airness.governance.ReportFixtures.mitigated;
import static eu.ciechanowiec.airness.governance.ReportFixtures.open;
import static eu.ciechanowiec.airness.governance.ReportFixtures.report;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ComplianceReportTest {

    @Test
    void attributesVerifiedToolLicensesWithoutInventingArchiveNotices() {
        String source = "https://example.test/release?name=\"license\"";
        ReportPackage entry = new ReportPackage(
            "group:tool:jar:1", List.of(MIT), List.of(new ReportUse(ROOT_MODULE, "build", DIRECT)),
            Map.of(
                ReportPackage.LICENSE_EVIDENCE_PREFIX + source, "Supplier declaration: Missing\n<script>unsafe</script>"
            )
        );
        assertTrue(entry.archiveNotices().isEmpty());
        String html = ComplianceReportHtml.render(report(List.of(entry), List.of()));
        assertTrue(html.contains("Licenses confirmed from release evidence"));
        assertTrue(html.contains("Supplier declaration: Missing"), "an unstructured evidence text is printed as is");
        assertTrue(html.contains("href=\"https://example.test/release?name=&quot;license&quot;\""));
        assertTrue(html.contains("&lt;script&gt;unsafe&lt;/script&gt;"));
        assertFalse(html.contains("<script>unsafe</script>"));
    }

    @Test
    void keepsProductionArchiveAbsencesVisibleDespiteSupplementalEvidence() {
        ReportPackage entry = new ReportPackage(
            "group:library:jar:1", List.of(MIT), List.of(new ReportUse(ROOT_MODULE, "compile", DIRECT)),
            Map.of(ReportPackage.LICENSE_EVIDENCE_PREFIX + "javascript:alert(1)", "Reviewed upstream source")
        );
        String html = ComplianceReportHtml.render(report(List.of(entry), List.of()));
        assertFalse(html.contains("href=\"javascript:"));
        assertFalse(html.contains("Every production artifact version ships a license file."));
        assertTrue(html.contains("Reviewed upstream source"));
    }

    @Test
    void classifiesOnlyAliasesTheBuildActuallyDeclares() {
        assertEquals(LicenseClass.PERMISSIVE, POLICY.classification(List.of("MIT License")));
        assertEquals(LicenseClass.WEAK_COPYLEFT, POLICY.classification(List.of("MPL-2.0")));
        assertEquals(LicenseClass.OUTSIDE_POLICY, POLICY.classification(List.of("Proprietary")));
        assertEquals(LicenseClass.OUTSIDE_POLICY, POLICY.classification(List.of()));
        assertEquals(LicenseClass.PERMISSIVE, POLICY.classification(List.of("Proprietary", MIT)));
        assertThrows(IllegalArgumentException.class, () -> new ReportLicensePolicy(Set.of(), Map.of()));
    }

    @Test
    void keepsDraftStatusSeparateFromTheDependencyVerdict() {
        ComplianceReport report = report(List.of(dependency(MIT)), List.of());
        assertTrue(report.inventory().withinPolicy());
        assertEquals(List.of("Uncommitted sources"), report.draftReasons());
        assertEquals("oss-compliance-report-sample-a12345678901", report.fileStem());
    }

    @Test
    void reportsPolicyFailuresWithoutLosingMitigatedFindings() {
        ReportAdvisory excused = mitigated("CVE-2026-1234", "HIGH", false);
        assertTrue(report(List.of(dependency(MIT)), List.of(excused)).inventory().withinPolicy());
        ReportAdvisory unresolved = open("CVE-2026-1235", "HIGH");
        assertFalse(report(List.of(dependency(MIT)), List.of(unresolved)).inventory().withinPolicy());
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
                .anyMatch(reason -> reason.contains("don't satisfy"))
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
        assertTrue(csv.startsWith("Artifact,License,Declared license,Class,Module,Scope,Relation\r\n"));
        assertTrue(csv.contains("\"module,one\",\"Production\",\"Direct\""));
        assertTrue(csv.contains("\"module\"\"two\",\"Test\",\"Transitive\""));
    }

    @Test
    void rendersEvidenceAndNoticesWithoutExecutingTheirMarkup() {
        ReportPackage entry = new ReportPackage(
            "group:library:jar:1", List.of("MIT License"),
            List.of(
                new ReportUse(ROOT_MODULE, "runtime", DIRECT),
                new ReportUse("other", "test\" onmouseover=\"alert(1)", "Transitive")
            ), Map.of("META-INF/LICENSE", "<script>alert(1)</script>")
        );
        String html = ComplianceReportHtml.render(report(List.of(entry, dependency("MPL-2.0")), List.of()));
        assertTrue(html.contains("data:font/woff2;base64,"));
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertTrue(html.contains("data-inventory=\"inventory-maven\""));
        assertTrue(html.contains("<colgroup>"), "the inventory fixes its column widths");
        assertTrue(html.contains("Weak copyleft"));
        assertTrue(html.contains(COMMIT));
        assertFalse(html.contains("<script>alert(1)</script>"));
        assertFalse(html.contains("<script src="));
        assertFalse(html.contains("\" onmouseover=\""));
    }

    @Test
    void rendersAnEmptyInventoryAndUnknownAdvisorySeverityExplicitly() {
        String empty = ComplianceReportHtml.render(report(List.of(), List.of()));
        assertTrue(empty.contains("No package version has a weak copyleft license."));
        assertTrue(empty.contains("No known vulnerability was found."));
        assertTrue(empty.contains("<li class=\"pass\">No known vulnerability was found.</li>"));
        ReportAdvisory advisory = new ReportAdvisory(
            "ADVISORY-1", "Unrated", "group:unsafe:1", List.of(ROOT_MODULE), "", Optional.empty(), List.of()
        );
        ReportPackage missing = new ReportPackage(
            "group:library:jar:1", List.of(), List.of(new ReportUse(ROOT_MODULE, "compile", DIRECT)), Map.of()
        );
        String failing = ComplianceReportHtml.render(report(List.of(missing), List.of(advisory)));
        assertTrue(failing.contains("Not declared"));
        assertTrue(failing.contains("<li class=\"fail\">1 vulnerability (1 unrated) is open"));
        assertTrue(failing.contains("<span class=\"sev sev-unrated\">Unrated</span>"));
        assertTrue(failing.contains("group:<wbr>unsafe 1"));
    }

    @Test
    void rendersCleanReportsWithSeparateDevelopmentTotals() {
        ReportPackage entry = new ReportPackage(
            "group:test:jar:1", List.of(MIT), List.of(new ReportUse(ROOT_MODULE, "test", DIRECT)), Map.of()
        );
        ComplianceReport original = report(List.of(entry), List.of());
        ComplianceReport clean = new ComplianceReport(
            original.project(), new ReportSource(COMMIT, "fingerprint", "now", List.of()),
            original.inventory(), original.evidence()
        );
        String html = ComplianceReportHtml.render(clean);
        assertFalse(html.contains("Draft: not release evidence"));
        assertTrue(
            html.contains(
                "1 package version is in use: 0 ship with the product, marked <em>Production</em>, and 1 is used "
                    + "only to"
            )
        );
        assertTrue(html.contains("Uncommitted changes</td><td>None"));
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
            List.of(new ReportUse(ROOT_MODULE, "provided", DIRECT), new ReportUse(ROOT_MODULE, "test", DIRECT)), Map
                .of()
        );
        assertFalse(entry.production());
        assertTrue(dependency(MIT).production());
    }

    @Test
    void groupsSharedNoticesAndNamesProductionArtifactsWithoutOne() {
        String text = "Copyright 2026 Example Authors.";
        ReportPackage first = new ReportPackage(
            "group:first:jar:1", List.of(MIT), List.of(new ReportUse(ROOT_MODULE, "compile", DIRECT)),
            Map.of("META-INF/LICENSE", text)
        );
        ReportPackage second = new ReportPackage(
            "group:second:jar:1", List.of(MIT), List.of(new ReportUse(ROOT_MODULE, "runtime", DIRECT)),
            Map.of("META-INF/LICENSE.txt", text)
        );
        ComplianceReport original = report(List.of(first, second, dependency(MIT)), List.of());
        ComplianceReport dirty = new ComplianceReport(
            original.project(), new ReportSource(COMMIT, "fingerprint", "now", List.of(ReportSource.DIRTY)),
            original.inventory(), original.evidence()
        );
        String html = ComplianceReportHtml.render(dirty);
        assertTrue(
            html.contains(
                "<p class=\"title\">Shared by 2 package versions</p><p class=\"notice-shared\">group:<wbr>first 1, "
                    + "group:<wbr>second 1</p><p class=\"notice-packages\">META-INF/LICENSE, META-INF/LICENSE.txt</p>"
            )
        );
        assertTrue(html.contains("1 package version of the 3 the product ships contains no license file"));
        assertTrue(html.contains("<li><code>group:library 1</code></li>"));
        assertTrue(html.contains("Uncommitted changes</td><td>Present, so this report is a draft"));
        assertTrue(html.contains("<span class=\"digest\">digest</span>"));
        assertTrue(dirty.source().dirty());
        String empty = ComplianceReportHtml.render(report(List.of(), List.of()));
        assertTrue(empty.contains("No package ships with the product, so no license file is reproduced."));
        assertFalse(empty.contains("contains a license file."), "an empty product has no contradictory empty states");
        String complete = ComplianceReportHtml.render(report(List.of(first, second), List.of()));
        assertTrue(complete.contains("Each of the 2 package versions the product ships contains a license file."));
        assertTrue(complete.contains("1 distinct text covers the 2 package versions that contain one"));
    }

    @Test
    void reproducesTheLicenseFilesOfShippedNpmPackagesOnly() {
        ReportPackage shipped = new ReportPackage(
            "pkg:npm/%40scope/runtime@1.0.0", List.of(MIT),
            List.of(new ReportUse(ROOT_MODULE, ReportUse.PRODUCTION, "Transitive")), Map.of("LICENSE", "Runtime text.")
        );
        ReportPackage tooling = new ReportPackage(
            "pkg:npm/linter@2.0.0", List.of(MIT), List.of(new ReportUse(ROOT_MODULE, ReportUse.DEVELOPMENT, DIRECT)),
            Map.of("LICENSE", "Linter text.")
        );
        assertTrue(shipped.production());
        assertFalse(tooling.production());
        String html = ComplianceReportHtml.render(report(List.of(shipped, tooling), List.of()));
        assertTrue(html.contains("<p class=\"title\">@scope/runtime 1.0.0</p>"));
        assertTrue(html.contains("Runtime text."));
        assertFalse(html.contains("Linter text."));
        assertTrue(html.contains("data-inventory=\"inventory-npm\""));
        assertTrue(html.contains("<td>@scope/<wbr>runtime</td><td><span class=\"mono\">1.0.0</span></td>"));
        assertTrue(html.contains("Production, transitive"));
        assertTrue(html.contains("Development, direct"));
    }

    @Test
    void showsEachLicenseByThePolicyIdentifierAndListsTheSpellingsItRead() {
        ReportPackage aliased = new ReportPackage(
            "group:aliased:jar:1", List.of("MIT License"), List.of(new ReportUse(ROOT_MODULE, "compile", DIRECT)),
            Map.of()
        );
        ComplianceReport report = report(List.of(aliased, dependency(MIT)), List.of());
        assertEquals("MIT", aliased.licenseIds(POLICY));
        String html = ComplianceReportHtml.render(report);
        assertTrue(
            html.contains("<td>MIT License</td><td><span class=\"license\"><code>MIT</code></span></td><td>1</td>")
        );
        assertFalse(html.contains("<span class=\"label\">MIT License</span>"), "the chart counts one MIT");
        assertTrue(html.contains("<span class=\"label\">MIT</span>"));
        assertTrue(report.csv().contains("\"MIT\",\"MIT License\""));
    }

    @Test
    void listsTheSystemPackagesOfShippedImagesWithoutJudgingThem() {
        ReportImagePackage busybox = new ReportImagePackage(
            "Dockerfile, on Alpine 3.24.4", "pkg:apk/alpine/busybox@1.37.0-r0?distro=3.24.4", List.of("GPL-2.0-only"),
            true
        );
        ComplianceReport base = report(List.of(dependency(MIT)), List.of());
        ComplianceReport report = new ComplianceReport(
            base.project(), base.source(),
            new ReportInventory(base.inventory().packages(), List.of(), POLICY, List.of(busybox)), base.evidence()
        );
        assertTrue(report.inventory().withinPolicy(), "an image's system packages are not judged");
        String html = ComplianceReportHtml.render(report);
        assertTrue(html.contains("<p class=\"table-title\">Dockerfile, on Alpine 3.24.4</p>"));
        assertTrue(html.contains("<td>alpine/<wbr>busybox</td><td><span class=\"mono\">1.37.0-r0</span></td>"));
        assertTrue(html.contains("<code>GPL-2.0-only</code>"));
        assertTrue(html.contains("listed from the built image and looked up in OSV"));
        String plain = ComplianceReportHtml.render(base);
        assertTrue(plain.contains("No container image was scanned."));
    }
}
