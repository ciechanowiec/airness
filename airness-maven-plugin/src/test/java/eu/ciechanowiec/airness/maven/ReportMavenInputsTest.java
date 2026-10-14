package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.ReportLicensePolicy;
import eu.ciechanowiec.airness.governance.ReportPackage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.SneakyThrows;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportMavenInputsTest {

    private static final String ARCHIVE_TYPE = "jar";

    @TempDir
    private Path directory;

    @Test
    @SneakyThrows
    void suppliesTheNativeCheckWithOnlyVerifiedMissingDeclarations() {
        Path local = this.directory.resolve("repository/example/library/1.0");
        Files.createDirectories(local);
        Files.writeString(
            local.resolve("library-1.0.pom"), """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>library</artifactId>
                <version>1.0</version></project>
                """
        );
        Artifact artifact = artifact();
        String content = "reviewed fixture artifact";
        artifact.setFile(Files.writeString(local.resolve("library-1.0.jar"), content).toFile());
        MavenProject project = this.project();
        project.setResolvedArtifacts(Set.of(artifact));
        LicenseMetadata entry = new LicenseMetadata(
            MavenReportArtifacts.identity(artifact), ReportJson.hash(content), "Apache-2.0",
            new LicenseEvidence(List.of(), "https://example.test/release", "Missing POM metadata", Map.of())
        );
        try (ReportMavenFixture fixture = new ReportMavenFixture(this.directory)) {
            new MavenLicenseMetadata(fixture.builder(), fixture.request(), List.of(entry)).prepare(project);
        }
        Path output = Path.of(project.getBuild().getDirectory()).resolve("airness/licenses/THIRD-PARTY.properties");
        assertTrue(Files.readString(output).contains("example--library--1.0=Apache-2.0"));
    }

    @Test
    @SneakyThrows
    void refusesAlteredBytesBeforeWritingNativeLicenseCorrections() {
        Path local = this.directory.resolve("repository/xalan/serializer/2.7.3");
        Files.createDirectories(local);
        Files.writeString(
            local.resolve("serializer-2.7.3.pom"), """
                <project><modelVersion>4.0.0</modelVersion><groupId>xalan</groupId><artifactId>serializer</artifactId>
                <version>2.7.3</version></project>
                """
        );
        Artifact artifact = new DefaultArtifact(
            "xalan", "serializer", "2.7.3", "test", ARCHIVE_TYPE, "", new DefaultArtifactHandler(ARCHIVE_TYPE)
        );
        artifact.setFile(Files.writeString(local.resolve("serializer-2.7.3.jar"), "altered bytes").toFile());
        MavenProject project = this.project();
        project.setResolvedArtifacts(Set.of(artifact));
        try (ReportMavenFixture fixture = new ReportMavenFixture(this.directory)) {
            MavenLicenseMetadata metadata = new MavenLicenseMetadata(fixture.builder(), fixture.request());
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> metadata.prepare(project));
            assertTrue(failure.toString().contains("checksum mismatch"));
        }
    }

    @Test
    @SneakyThrows
    void refusesCorrectionsThatTheNativeFormatWouldApplyToAnotherClassifier() {
        MavenProject project = this.project();
        Artifact ordinary = new DefaultArtifact(
            "xalan", "serializer", "2.7.3", "test", ARCHIVE_TYPE, "", new DefaultArtifactHandler(ARCHIVE_TYPE)
        );
        Artifact classified = new DefaultArtifact(
            "xalan", "serializer", "2.7.3", "test", ARCHIVE_TYPE, "tests", new DefaultArtifactHandler(ARCHIVE_TYPE)
        );
        project.setResolvedArtifacts(Set.of(ordinary, classified));
        try (ReportMavenFixture fixture = new ReportMavenFixture(this.directory)) {
            MavenLicenseMetadata metadata = new MavenLicenseMetadata(fixture.builder(), fixture.request());
            assertThrows(IllegalStateException.class, () -> metadata.prepare(project));
        }
    }

    @Test
    @SneakyThrows
    void clearsStaleCorrectionsAndLeavesUnknownArtifactsUncorrected() {
        MavenProject project = this.project();
        project.setResolvedArtifacts(Set.of(artifact()));
        Path output = Path.of(project.getBuild().getDirectory()).resolve("airness/licenses/THIRD-PARTY.properties");
        Files.createDirectories(output.getParent());
        Files.writeString(output, "example--library--1.0=MIT\n");
        try (ReportMavenFixture fixture = new ReportMavenFixture(this.directory)) {
            new MavenLicenseMetadata(fixture.builder(), fixture.request()).prepare(project);
        }
        assertFalse(Files.readString(output).contains("example--library"));
        assertTrue(MavenLicenseMetadata.verified(artifact(), List.of()).isEmpty());
    }

    @Test
    @SneakyThrows
    void readsLicenseUrlsWithoutGuessingAnIdentifierForEmptyMetadata() {
        Path local = this.directory.resolve("repository/example/library/1.0");
        Files.createDirectories(local);
        Files.writeString(
            local.resolve("library-1.0.pom"), """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>library</artifactId>
                <version>1.0</version><licenses><license><name> </name><url>https://example.test/license</url></license>
                <license/></licenses></project>
                """
        );
        try (ReportMavenFixture fixture = new ReportMavenFixture(this.directory)) {
            assertEquals(
                List.of("https://example.test/license", "Unknown"),
                new MavenLicenseMetadata(fixture.builder(), fixture.request()).declarations(artifact())
            );
        }
    }

    @Test
    @SneakyThrows
    void keepsArtifactsTransitiveWhenOnlyOtherCoordinatesAreDeclared() {
        MavenProject project = this.project();
        Artifact artifact = artifact();
        artifact.setScope("compile");
        artifact.setFile(Files.writeString(this.directory.resolve("dependency.pom"), "<project/>").toFile());
        project.setResolvedArtifacts(Set.of(artifact));
        for (String coordinate : List.of("other:library:1.0", "example:other:1.0", "example:library:2.0")) {
            Dependency declaration = declaration(coordinate);
            project.getModel().setDependencies(List.of(declaration));
            ReportPackage entry = MavenReportArtifacts.dependencies(
                project, Map.of("example:library:1.0", List.of("MIT"))
            ).getFirst();
            assertEquals("Transitive", entry.uses().getFirst().relation());
            assertTrue(entry.production());
        }
    }

    @Test
    @SneakyThrows
    void retainsFinishedModulesDependenciesWithoutChangingMavensFilter() {
        MavenProject project = this.project();
        Artifact artifact = artifact();
        artifact.setScope("compile");
        artifact.setFile(Files.writeString(this.directory.resolve("dependency.pom"), "<project/>").toFile());
        project.setResolvedArtifacts(Set.of(artifact));
        assertTrue(project.getArtifacts().isEmpty());
        Map<String, List<String>> licenses = Map.of("example:library:1.0", List.of("MIT"));
        assertTrue(MavenReportArtifacts.dependencies(project, licenses).getFirst().production());
        assertTrue(project.getArtifacts().isEmpty());
        project.setResolvedArtifacts(Set.of());
        assertThrows(IllegalStateException.class, () -> MavenReportArtifacts.dependencies(project, licenses));
    }

    @Test
    void readsTheActualInheritedAllowlistAndMerges() {
        MavenProject project = this.project();
        ReportLicensePolicy policy = MavenReportPolicy.from(project);
        assertEquals("MIT", policy.normalized("MIT License"));
        assertEquals("Outside policy", policy.classification(List.of("GPL-3.0")));
        assertThrows(IllegalStateException.class, () -> MavenReportPolicy.from(InputProjects.project(this.directory)));
    }

    @Test
    @SneakyThrows
    void usesMavensResolvedVersionsAndPreservesTheNativeScopes() {
        MavenProject project = this.project();
        Artifact artifact = artifact();
        artifact.setFile(Files.writeString(this.directory.resolve("dependency.pom"), "<project/>").toFile());
        project.setResolvedArtifacts(Set.of(artifact));
        Dependency declared = new Dependency();
        declared.setGroupId("example");
        declared.setArtifactId("library");
        declared.setVersion("1.0");
        project.getModel().addDependency(declared);
        List<ReportPackage> packages = MavenReportArtifacts.dependencies(
            project, Map.of("example:library:1.0", List.of("MIT"))
        );
        assertEquals("example:library:jar:1.0", packages.getFirst().coordinate());
        assertEquals("test", packages.getFirst().uses().getFirst().scope());
        assertEquals("Direct", packages.getFirst().uses().getFirst().relation());
        assertThrows(IllegalStateException.class, () -> MavenReportArtifacts.dependencies(project, Map.of()));
        Artifact classified = new DefaultArtifact(
            "example", "library", "1.0", "test", ARCHIVE_TYPE, "tests",
            new DefaultArtifactHandler(ARCHIVE_TYPE)
        );
        assertEquals("example:library:jar:tests:1.0", MavenReportArtifacts.identity(classified));
    }

    @Test
    @SneakyThrows
    void buildsToolLicenseEvidenceThroughTheRealOfflineMavenModelBuilder() {
        Path local = this.directory.resolve("repository/example/library/1.0");
        Files.createDirectories(local);
        Files.writeString(
            local.resolve("library-1.0.pom"), """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>library</artifactId>
                <version>1.0</version><licenses><license><name>MIT License</name></license></licenses>
                <build><plugins><plugin><artifactId>maven-jar-plugin</artifactId></plugin>
                <plugin><artifactId>maven-jar-plugin</artifactId></plugin></plugins></build></project>
                """
        );
        try (ReportMavenFixture fixture = new ReportMavenFixture(this.directory)) {
            MavenReportArtifacts reader = new MavenReportArtifacts(
                fixture.builder(), fixture.request()
            );
            ReportPackage entry = reader.tool(artifact(), "module");
            assertEquals(List.of("MIT License"), entry.licenses());
            PluginResolution resolution = ReportMavenFixture.resolution(
                "module", "example:library:1.0"
            );
            PluginResolution transitive = ReportMavenFixture.resolution(
                "other-module", "example:tool:1.0"
            );
            PluginReportInputs.write(this.directory.resolve("tools"), List.of(resolution, transitive), reader);
            assertEquals(
                "Direct", ReportJson.read(this.directory.resolve("tools/report-inventory.json"))
                    .path("packages").path(0).path("uses").path(0).path("relation").asText()
            );
            assertEquals(
                "Transitive", ReportJson.read(this.directory.resolve("tools/report-inventory.json"))
                    .path("packages").path(0).path("uses").path(1).path("relation").asText()
            );
        }
    }

    @Test
    @SneakyThrows
    void requiresCurrentToolEvidenceWhenTheModuleBindsTheToolScanner() {
        MavenProject project = this.project();
        project.setResolvedArtifacts(Set.of());
        Instant started = Instant.parse("2026-10-13T12:00:00Z");
        Path build = Path.of(project.getBuild().getDirectory());
        ReportJson.write(
            build.resolve("airness/compliance/licenses.json"), Map.of(
                "generatedAt", started.toString(), "packages", List.of()
            )
        );
        ReportJson.write(
            build.resolve("dependency-check-report.json"), Map.of(
                "projectInfo", Map.of("reportDate", started.toString()),
                "dependencies", List.of(), "scanInfo", Map.of()
            )
        );
        ReportJson.write(build.resolve("bom.json"), Map.of("bomFormat", "CycloneDX", "components", List.of()));
        ReportModule module = new ReportInputs(started).read(project);
        assertTrue(module.packages().isEmpty());
        assertFalse(module.evidence().getOrDefault("SBOM SHA-256", "").isBlank());
        Plugin tools = InputProjects.plugin(project, "airness-maven-plugin", "<configuration/>");
        PluginExecution execution = new PluginExecution();
        execution.addGoal("plugin-inputs");
        tools.addExecution(execution);
        assertThrows(IllegalStateException.class, () -> new ReportInputs(started).read(project));
        ReportJson.write(
            build.resolve("airness/plugin-inputs/report-inventory.json"), Map.of(
                "generatedAt", started.toString(),
                "packages", List.of(), "dependencies", Map.of()
            )
        );
        assertEquals(module.packages(), new ReportInputs(started).read(project).packages());
        Files.setLastModifiedTime(build.resolve("bom.json"), FileTime.from(started.minusSeconds(1)));
        assertThrows(IllegalStateException.class, () -> new ReportInputs(started).read(project));
    }

    private MavenProject project() {
        MavenProject project = InputProjects.project(this.directory);
        project.setGroupId("example");
        project.setVersion("1.0");
        Plugin plugin = InputProjects.plugin(
            project, "license-maven-plugin", """
                <configuration><includedLicenses><includedLicense>MIT</includedLicense></includedLicenses>
                <licenseMerges><licenseMerge>MIT|MIT License</licenseMerge></licenseMerges></configuration>
                """
        );
        plugin.setGroupId("org.codehaus.mojo");
        return project;
    }

    private static Artifact artifact() {
        return new DefaultArtifact(
            "example", "library", "1.0", "test", ARCHIVE_TYPE, "", new DefaultArtifactHandler(ARCHIVE_TYPE)
        );
    }

    private static Dependency declaration(String coordinate) {
        String[] parts = coordinate.split(":", -1);
        Dependency dependency = new Dependency();
        dependency.setGroupId(parts[0]);
        dependency.setArtifactId(parts[1]);
        dependency.setVersion(parts[2]);
        return dependency;
    }
}
