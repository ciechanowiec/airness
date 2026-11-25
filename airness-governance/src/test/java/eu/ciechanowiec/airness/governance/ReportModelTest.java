package eu.ciechanowiec.airness.governance;

import static eu.ciechanowiec.airness.governance.ReportFixtures.COMMIT;
import static eu.ciechanowiec.airness.governance.ReportFixtures.DIRECT;
import static eu.ciechanowiec.airness.governance.ReportFixtures.MIT;
import static eu.ciechanowiec.airness.governance.ReportFixtures.POLICY;
import static eu.ciechanowiec.airness.governance.ReportFixtures.ROOT_MODULE;
import static eu.ciechanowiec.airness.governance.ReportFixtures.dependency;
import static eu.ciechanowiec.airness.governance.ReportFixtures.report;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The values the report reads its evidence through: coordinates, uses, verified license evidence, and the markup
 * they are printed with.
 */
class ReportModelTest {

    private static final String APACHE = "Apache-2.0";
    private static final String SHA = "0123456789abcdef";
    private static final int SHORT = 12;
    private static final int LONG = 400;

    @Test
    void readsEveryCoordinateAsAPackageAndAVersion() {
        assertEquals(new ReportCoordinate("group:artifact", "1.0"), ReportCoordinate.of("group:artifact:jar:1.0"));
        assertEquals(
            new ReportCoordinate("group:plugin (maven-plugin)", "2.0"),
            ReportCoordinate.of("group:plugin:maven-plugin:2.0")
        );
        assertEquals(
            new ReportCoordinate("group:artifact:tests (test-jar)", "3.0"),
            ReportCoordinate.of("group:artifact:test-jar:tests:3.0")
        );
        assertEquals(new ReportCoordinate("group:artifact", "4.0"), ReportCoordinate.of("group:artifact:4.0"));
        assertEquals(new ReportCoordinate("@scope/kit", "2.0.0"), ReportCoordinate.of("pkg:npm/%40scope/kit@2.0.0"));
        assertEquals(
            new ReportCoordinate("group:library", "1"), ReportCoordinate.of("pkg:maven/group/library@1?type=jar")
        );
        assertEquals(new ReportCoordinate("left-pad", ""), ReportCoordinate.of("pkg:npm/left-pad"));
        assertEquals(new ReportCoordinate("bundle.js", ""), ReportCoordinate.of("bundle.js"));
        assertEquals(new ReportCoordinate("a name: with spaces", ""), ReportCoordinate.of("a name: with spaces"));
    }

    @Test
    void groupsEveryCoordinateByItsEcosystem() {
        assertEquals("npm", ReportCoordinate.ecosystem("pkg:npm/left-pad@1.3.0"));
        assertEquals("maven", ReportCoordinate.ecosystem("pkg:maven/group/library@1"));
        assertEquals("maven", ReportCoordinate.ecosystem("group:library:jar:1"));
        assertEquals("generic", ReportCoordinate.ecosystem("pkg:generic/node@26.13.0"));
        assertEquals("generic", ReportCoordinate.ecosystem("pkg:apk/alpine/zlib@1.3.2-r1"));
    }

    @Test
    void namesEveryUseTheSameWayWhateverItsEcosystem() {
        assertEquals("Production", new ReportUse(ROOT_MODULE, "compile", DIRECT).label());
        assertEquals("Production", new ReportUse(ROOT_MODULE, "runtime", DIRECT).label());
        assertEquals("Production", new ReportUse(ROOT_MODULE, ReportUse.PRODUCTION, DIRECT).label());
        assertEquals("Provided", new ReportUse(ROOT_MODULE, "system", DIRECT).label());
        assertEquals("Development", new ReportUse(ROOT_MODULE, ReportUse.DEVELOPMENT, DIRECT).label());
        assertEquals("Build", new ReportUse(ROOT_MODULE, ReportUse.BUILD, DIRECT).label());
        assertEquals("Import", new ReportUse(ROOT_MODULE, "import", DIRECT).label());
        assertEquals("", new ReportUse(ROOT_MODULE, "", "").label());
        assertEquals("Transitive", new ReportUse(ROOT_MODULE, ReportUse.PRODUCTION, "transitive").relationLabel());
        assertTrue(new ReportUse(ROOT_MODULE, ReportUse.PRODUCTION, DIRECT).production());
        assertFalse(new ReportUse(ROOT_MODULE, ReportUse.DEVELOPMENT, DIRECT).production());
    }

    @Test
    void carriesVerifiedLicenseEvidenceThroughANoticeValueUnchanged() {
        Map<String, String> texts = new LinkedHashMap<>();
        texts.put("Release LICENSE", "The license text.");
        texts.put("Release NOTICE", "The notice text.");
        ReportLicenseEvidence evidence = new ReportLicenseEvidence(
            APACHE, SHA, List.of("Apache License", "MIT"), "The POM has no license.", texts
        );
        assertEquals(Optional.of(evidence), ReportLicenseEvidence.decode(evidence.encode()));
        ReportLicenseEvidence missing = new ReportLicenseEvidence(APACHE, SHA, List.of(), "No POM license.", Map.of());
        assertEquals(Optional.of(missing), ReportLicenseEvidence.decode(missing.encode()));
        assertEquals(Optional.empty(), ReportLicenseEvidence.decode("Supplier declaration: Missing"));
        assertEquals(
            Optional.empty(), ReportLicenseEvidence.decode(missing.encode() + "\u001Ea label without its text"),
            "a text without its separator is no evidence"
        );
    }

    @Test
    void printsVerifiedLicenseEvidenceAsFactsAndTexts() {
        ReportLicenseEvidence evidence = new ReportLicenseEvidence(
            APACHE, SHA, List.of(), "The POM has no license.", Map.of("Release LICENSE", "<<<<<<\nThe license text.")
        );
        ReportPackage tool = new ReportPackage(
            "group:tool:jar:1", List.of(APACHE), List.of(new ReportUse(ROOT_MODULE, ReportUse.BUILD, DIRECT)),
            Map.of(ReportPackage.LICENSE_EVIDENCE_PREFIX + "https://example.test/release", evidence.encode())
        );
        String html = ComplianceReportHtml.render(report(List.of(tool), List.of()));
        assertTrue(
            html.contains("<td>File fingerprint (SHA-256)</td><td><span class=\"digest\">" + SHA + "</span></td>")
        );
        assertTrue(html.contains("<td>License in the published metadata</td><td>None stated</td>"));
        assertTrue(html.contains("<td>How the evidence shows it</td><td>The POM has no license.</td>"));
        assertTrue(html.contains("<p class=\"notice-packages\">Release LICENSE</p>"));
        assertTrue(html.contains("<hr>"), "a delimiter line is drawn as a rule");
        assertFalse(html.contains("&lt;&lt;&lt;"), "a delimiter line is never printed as characters");
        ReportLicenseEvidence declared = new ReportLicenseEvidence(APACHE, SHA, List.of("ASL"), "Reason.", Map.of());
        ReportPackage other = new ReportPackage(
            "group:other:jar:1", List.of(APACHE), List.of(new ReportUse(ROOT_MODULE, ReportUse.BUILD, DIRECT)),
            Map.of(ReportPackage.LICENSE_EVIDENCE_PREFIX + "release notes", declared.encode())
        );
        String plain = ComplianceReportHtml.render(report(List.of(other), List.of()));
        assertTrue(plain.contains("<td>License in the published metadata</td><td>ASL</td>"));
        assertTrue(plain.contains("<td>Where the license was found</td><td>release notes</td>"));
    }

    @Test
    void namesTheModuleOfEachUseWhenSeveralModulesTakePart() {
        ReportPackage shared = new ReportPackage(
            "group:shared:jar:1", List.of(MIT),
            List.of(new ReportUse(ROOT_MODULE, "compile", DIRECT), new ReportUse("other", "test", "Transitive")),
            Map.of()
        );
        String html = ComplianceReportHtml.render(report(List.of(shared), List.of()));
        assertTrue(html.contains("<th scope=\"col\">Module: use</th>"));
        assertTrue(html.contains("other: Test, transitive<br>root: Production, direct"));
        assertTrue(html.contains("its row names each module with the package&#39;s use there"));
        String single = ComplianceReportHtml.render(report(List.of(dependency(MIT)), List.of()));
        assertTrue(single.contains("All packages belong to the project&#39;s single build module, <code>root</code>."));
    }

    @Test
    void listsAPackageAnImageHoldsOnlyWhenTheInventoryDoesNot() {
        ReportPackage npm = new ReportPackage(
            "pkg:npm/listed@1.0.0", List.of(MIT), List.of(new ReportUse(ROOT_MODULE, ReportUse.PRODUCTION, DIRECT)),
            Map.of()
        );
        String image = "web/Dockerfile, on Alpine 3.24.4";
        ComplianceReport base = report(List.of(npm), List.of());
        ComplianceReport report = new ComplianceReport(
            base.project(), base.source(), new ReportInventory(
                List.of(npm), List.of(), POLICY, List.of(
                    new ReportImagePackage(image, "pkg:npm/listed@1.0.0?arch=x64", List.of(MIT), false),
                    new ReportImagePackage(image, "pkg:npm/bundled@2.0.0", List.of(), false)
                )
            ), base.evidence()
        );
        String html = ComplianceReportHtml.render(report);
        assertTrue(html.contains("<td>bundled</td><td><span class=\"mono\">2.0.0</span></td>"));
        assertTrue(html.contains("<span class=\"empty\">Not declared</span>"));
        assertFalse(
            html.contains("<td>listed</td><td><span class=\"mono\">1.0.0</span></td><td><span class=\"empty\">")
        );
    }

    @Test
    void namesTheProjectAndCommitAtTheTopOfEveryPrintedPageWithoutBreakingItsStyle() {
        ComplianceReport base = report(List.of(), List.of());
        ComplianceReport quoted = new ComplianceReport(
            "a \"quoted\" <name>\\", base.source(), base.inventory(), base.evidence()
        );
        String html = ComplianceReportHtml.render(quoted);
        assertTrue(
            html.contains(
                "@top-right{content:\"Open source software report | a quoted name | "
                    + COMMIT.substring(0, SHORT) + "\""
            )
        );
        assertTrue(html.contains("@page:first{@top-right{content:none}}"), "the cover has no running header");
    }

    @Test
    void namesOnlyTheSourcesThatTookPartAndPrintsALongDescriptionCutAndMarked() {
        ReportPackage npm = new ReportPackage(
            "pkg:npm/kit@1.0.0", List.of(MIT), List.of(new ReportUse(ROOT_MODULE, ReportUse.DEVELOPMENT, DIRECT)),
            Map.of()
        );
        ReportAdvisory advisory = new ReportAdvisory(
            "GHSA-aaaa-bbbb-cccc", "MODERATE", "pkg:npm/kit@1.0.0", List.of(ROOT_MODULE), "word ".repeat(LONG),
            Optional.of(ReportMitigation.of("Added 2026-10-13. Unused. Mitigation: Removed.", false)), List.of()
        );
        String html = ComplianceReportHtml.render(report(List.of(npm), List.of(advisory)));
        assertFalse(html.contains("OWASP"), "no Maven artifact was scanned by Dependency-Check");
        assertTrue(html.contains("looked up in OSV, an open vulnerability database"));
        assertTrue(html.contains("<td>Vulnerability data for JavaScript (npm) packages as of</td>"));
        assertTrue(html.contains("word [...]</p>"), "a long description is cut and the cut is marked");
        assertTrue(html.contains("<span class=\"sev sev-medium\">Medium</span>"));
        String maven = ComplianceReportHtml.render(report(List.of(dependency(MIT)), List.of()));
        assertTrue(maven.contains("OWASP Dependency-Check"));
        assertFalse(maven.contains("<td>Vulnerability data for JavaScript (npm) packages as of</td>"));
    }

    @Test
    void breaksAPackageNameOnlyAfterItsSeparators() {
        assertEquals("group:<wbr>artifact", ReportMarkup.breakable("group:artifact"));
        assertEquals("@scope/<wbr>kit", ReportMarkup.breakable("@scope/kit"));
        assertEquals("org.<wbr>example", ReportMarkup.breakable("org.example"));
        assertEquals("1.2.3", ReportMarkup.breakable("1.2.3"));
        assertTrue(ReportMarkup.table(List.of("A"), List.of(10), List.of(), "Nothing.").contains("Nothing."));
        assertFalse(ReportMarkup.tableStart(List.of("A"), List.of(), "").contains("colgroup"));
    }
}
