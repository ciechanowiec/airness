package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.ReportAdvisory;
import eu.ciechanowiec.airness.governance.ReportLicensePolicy;
import eu.ciechanowiec.airness.governance.ReportPackage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.SneakyThrows;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The npm evidence of a declared node project is read where its gates wrote it, judged by the Maven license
 * allowlist, folded into the compliance evidence, and a missing piece fails rather than reading as empty.
 */
class NpmEvidenceTest {

    private static final ReportLicensePolicy POLICY = new ReportLicensePolicy(Set.of("MIT", "Apache-2.0"), Map.of());

    @TempDir
    private Path directory;

    @Test
    void readsTheInventoryAndTheSbomOfEveryDeclaredNodeProject() {
        MavenProject project = NpmFixture.module(this.directory, true);
        List<NpmEvidence> evidence = NpmEvidence.of(project);
        assertEquals(1, evidence.size());
        NpmEvidence web = evidence.getFirst();
        assertEquals("web", web.key());
        assertEquals(
            List.of(this.directory.resolve("web").toAbsolutePath().normalize()), NpmEvidence.directories(project)
        );
        assertEquals("pkg:npm/%40scope/kit@2.0.0", web.packages().get(1).purl());
        assertEquals("CycloneDX", web.sbom().path("bomFormat").asText());
        assertFalse(web.wiringFailed());
    }

    @Test
    void readsNoEvidenceOfAHarnessParent() {
        MavenProject project = NpmFixture.module(this.directory, true);
        project.setGroupId("eu.ciechanowiec");
        project.setArtifactId("airness-parent-node");
        assertEquals(List.of(), NpmEvidence.of(project));
    }

    @Test
    void failsAMissingOrMalformedInventoryRatherThanReadingItAsEmpty() {
        NpmEvidence web = NpmEvidence.of(NpmFixture.module(this.directory, false)).getFirst();
        IllegalStateException missing = assertThrows(IllegalStateException.class, web::packages);
        assertTrue(
            Objects.requireNonNull(missing.getMessage()).contains("rather than passing over an empty inventory")
        );
        assertThrows(IllegalStateException.class, web::sbom);
        List<Findings> findings = new NpmLicenses(POLICY).findings(List.of(web));
        assertEquals(1, findings.getFirst().offences().size(), "a missing inventory is a finding");
    }

    @Test
    @SneakyThrows
    void failsAnInventoryOrSbomOfTheWrongShape() {
        NpmEvidence web = NpmEvidence.of(NpmFixture.module(this.directory, true)).getFirst();
        Files.writeString(web.results().resolve("dependencies.json"), "{\"contract\": 1}");
        Files.writeString(web.results().resolve("bom.cdx.json"), "{\"bomFormat\": \"SPDX\"}");
        assertThrows(IllegalStateException.class, web::packages);
        assertThrows(IllegalStateException.class, web::sbom);
        Files.writeString(web.results().resolve("dependencies.json"), "{");
        assertThrows(IllegalStateException.class, web::packages);
    }

    @Test
    @SneakyThrows
    void judgesInstalledPackagesByTheOneAllowlistAndNamesUnwiredEvidence() {
        NpmEvidence web = NpmEvidence.of(NpmFixture.module(this.directory, true)).getFirst();
        Files.writeString(web.results().resolve("wiring.json"), "{\"status\": \"failed\"}");
        List<Findings> findings = new NpmLicenses(POLICY).findings(List.of(web));
        assertEquals(List.of(), findings.getFirst().offences());
        assertEquals(List.of(web.subject()), findings.get(1).offences(), "evidence past a failing wiring is named");
        assertEquals(1, NpmReportInputs.qualifications(List.of(NpmFixture.module(this.directory, false))).size());
        assertEquals(
            List.of(web.subject() + ": left-pad@1.3.0 declares SSPL-1.0"), findings.get(2).offences(),
            "an expression with an admitted alternative passes, and a package this host did not install is left out"
        );
        assertEquals(1, NpmLicenses.elsewhere(List.of(web)));
    }

    @Test
    void foldsTheNpmHalfIntoTheModuleEvidenceWithItsAdvisories() {
        MavenProject project = NpmFixture.module(this.directory, true);
        try (ArchiveServer server = NpmFixture.osv()) {
            NpmReportInputs npm = NpmReportInputs.read(project, Instant.EPOCH, OsvScan.of(server.uri("/").toString()));
            List<String> coordinates = npm.packages(List.of()).stream().map(ReportPackage::coordinate).toList();
            assertEquals("pkg:npm/left-pad@1.3.0", coordinates.getFirst());
            assertTrue(coordinates.stream().anyMatch(coordinate -> coordinate.startsWith("pkg:generic/node@")));
            assertTrue(coordinates.stream().anyMatch(coordinate -> coordinate.startsWith("pkg:npm/%40pnpm/exe.")));
            assertEquals(List.of("pkg:npm/web@1.0.0"), npm.graph(Map.of()).get(project.getId()));
            assertTrue(npm.documents().contains("pkg:npm/%40scope/kit@2.0.0"));
            assertEquals(
                List.of("GHSA-0000-0000-0001"), npm.advisories().stream().map(ReportAdvisory::identity).toList()
            );
        }
        assertEquals(List.of(), NpmReportInputs.qualifications(List.of(project)), "scanned packages need no excuse");
    }

    @Test
    @SneakyThrows
    void aMitigationInOneNodeProjectDoesNotExcuseTheOtherProject() {
        MavenProject project = NpmFixture.module(this.directory, true);
        Files.createDirectories(this.directory.resolve("other"));
        Files.writeString(this.directory.resolve("other/package.json"), "{}");
        project.getProperties().setProperty("airness.node.projects", "web,other");
        NpmEvidence other = NpmEvidence.of(project).getLast();
        Files.createDirectories(other.results());
        Files.writeString(other.results().resolve(NpmEvidence.INVENTORY), NpmFixture.INVENTORY);
        Files.writeString(other.results().resolve(NpmEvidence.SBOM), NpmFixture.SBOM);
        NpmEvidence web = NpmEvidence.of(project).getFirst();
        Files.writeString(
            web.directory().resolve("package.json"), ReportJson.encode(
                Map.of(
                    "airness", Map.of(
                        "vulnerabilityAllowlist", List.of(
                            Map.of(
                                "package", "@scope/kit", "advisory", "GHSA-0000-0000-0001",
                                "reason", "This project does not load the affected parser.",
                                "mitigation", "Remove the package when the build tool upgrades.", "addedOn",
                                "2026-10-28"
                            )
                        )
                    )
                )
            )
        );
        try (ArchiveServer server = NpmFixture.osv()) {
            List<ReportAdvisory> advisories = NpmReportInputs.read(
                project, Instant.EPOCH, OsvScan.of(server.uri("/").toString())
            ).advisories();
            assertEquals(2, advisories.size());
            assertEquals(1L, advisories.stream().filter(ReportAdvisory::open).count());
        }
    }

    @Test
    void namesAnUnscopedAndAnUnversionedPackageAndOneDeclaringNoLicense() {
        NpmEvidence.NpmPackage app = new NpmEvidence.NpmPackage("app", "", "", true, true);
        assertEquals("pkg:npm/app", app.purl());
        NpmEvidence web = NpmEvidence.of(NpmFixture.module(this.directory, true)).getFirst();
        NpmEvidence bare = new NpmEvidence(web.module(), web.key(), web.directory(), this.directory.resolve("bare"));
        assertTrue(new NpmLicenses(POLICY).findings(List.of(bare)).getLast().clean(), "nothing read, nothing refused");
    }

    @Test
    @SneakyThrows
    void namesAnInstalledPackageThatDeclaresNoLicenseAndOneRefusedByName() {
        NpmEvidence web = NpmEvidence.of(NpmFixture.module(this.directory, true)).getFirst();
        Files.writeString(
            web.results().resolve("dependencies.json"),
            "{\"contract\": 1, \"packages\": [{\"name\": \"bare\", \"version\": \"1.0.0\", \"license\": \" \","
                + " \"direct\": true, \"installed\": true}, {\"name\": \"puppeteer\", \"version\": \"24.0.0\","
                + " \"license\": \"Apache-2.0\", \"direct\": false, \"installed\": false}]}"
        );
        List<Findings> findings = new NpmLicenses(POLICY).findings(List.of(web));
        assertEquals(List.of(web.subject() + ": bare@1.0.0 declares no license"), findings.get(2).offences());
        assertTrue(
            findings.getLast().offences().getFirst().startsWith(
                web.subject() + ": puppeteer@24.0.0 - downloads Chrome"
            ),
            "a package refused by name is refused whatever its license, installed here or not"
        );
    }

    @Test
    @SneakyThrows
    void refusesAnInventoryThatLeavesAFieldOut() {
        NpmEvidence web = NpmEvidence.of(NpmFixture.module(this.directory, true)).getFirst();
        Files.writeString(
            web.results().resolve("dependencies.json"),
            "{\"contract\": 1, \"packages\": [{\"name\": \"bare\", \"version\": \"1.0.0\", \"license\": \"MIT\"}]}"
        );
        assertThrows(IllegalStateException.class, web::packages, "a missing field is refused, never defaulted");
        String whole
            = "\"name\": \"a\", \"version\": \"1\", \"license\": \"MIT\", \"direct\": true, \"installed\": true";
        for (String field : List.of("name", "version", "license", "direct", "installed")) {
            String entry = whole.replace("\"" + field + "\":", "\"other-" + field + "\":");
            Files.writeString(
                web.results().resolve("dependencies.json"), "{\"contract\": 1, \"packages\": [{" + entry + "}]}"
            );
            assertThrows(IllegalStateException.class, web::packages, "an inventory without " + field);
        }
        Files.writeString(web.results().resolve("dependencies.json"), "{\"contract\": 2, \"packages\": []}");
        assertThrows(IllegalStateException.class, web::packages, "an unknown contract is refused");
    }

    @Test
    void readsNothingForAModuleWithoutNodeProjects() {
        MavenProject project = new MavenProject();
        project.getBuild().setDirectory(this.directory.resolve("target").toString());
        project.setFile(this.directory.resolve("pom.xml").toFile());
        NpmReportInputs npm = NpmReportInputs.read(project, Instant.EPOCH, OsvScan.of("http://127.0.0.1:9/"));
        assertEquals(List.of(), npm.packages(List.of()));
        assertEquals(List.of(), NpmReportInputs.qualifications(List.of(project)));
    }

    @Test
    void refusesAnSbomOlderThanTheInvocation() {
        MavenProject project = NpmFixture.module(this.directory, true);
        assertThrows(
            IllegalStateException.class, () -> NpmReportInputs.read(
                project, Instant.now().plus(Duration.ofMinutes(1)), OsvScan.of("http://127.0.0.1:9/")
            )
        );
    }

    @Test
    @SneakyThrows
    void joinsNoGraphEdgeForADocumentThatNamesNoProject() {
        MavenProject project = NpmFixture.module(this.directory, true);
        NpmEvidence web = NpmEvidence.of(project).getFirst();
        Files.writeString(web.results().resolve("bom.cdx.json"), "{\"bomFormat\": \"CycloneDX\", \"components\": []}");
        try (ArchiveServer server = NpmFixture.osv()) {
            NpmReportInputs npm = NpmReportInputs.read(project, Instant.EPOCH, OsvScan.of(server.uri("/").toString()));
            assertEquals(Map.of(), npm.graph(Map.of()));
        }
    }
}
