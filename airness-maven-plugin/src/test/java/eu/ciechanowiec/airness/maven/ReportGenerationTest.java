package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.ciechanowiec.airness.governance.ComplianceReport;
import eu.ciechanowiec.airness.governance.ReportLicensePolicy;
import eu.ciechanowiec.airness.governance.ReportSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultSessionData;
import org.eclipse.aether.SessionData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportGenerationTest {

    private static final int GIT_TIMEOUT = 10;

    @TempDir
    private Path directory;

    @Test
    @SneakyThrows
    void writesThePairWithTheExactDownloadBytesAndTheirHash() {
        Path root = this.repository();
        ReportProject project = project(root);
        SessionData data = new DefaultSessionData();
        ReportSession.begin(data, root);
        ReportGeneration generation = new ReportGeneration(ReportSession.source(data), project, List.of());
        ReportModule module = module();
        ComplianceReport report = generation.write(List.of(module), Optional.empty());
        assertTrue(
            generation.reportOnly().assemble(List.of(module)).draftReasons().stream()
                .anyMatch(reason -> reason.startsWith("Report-only invocation"))
        );
        Path htmlFile = project.build().resolve("compliance/" + report.fileStem() + ".html");
        String html = Files.readString(htmlFile);
        assertTrue(html.contains(report.source().commit()));
        assertEquals(
            report.evidence().sbom(), new ObjectMapper().readTree(report.evidence().downloads()).path("sbom").asText()
        );
        assertEquals(
            ReportJson.hash(report.evidence().sbom()), report.evidence().facts().get("Aggregate SBOM SHA-256")
        );
        assertTrue(Files.size(htmlFile.resolveSibling(report.fileStem() + ".pdf")) > 0);
    }

    @Test
    @SneakyThrows
    void refusesStaleSourceRatherThanCertifyingItUnderTheNewFingerprint() {
        Path root = this.repository();
        SessionData data = new DefaultSessionData();
        ReportSession.begin(data, root);
        ReportSource before = ReportSession.source(data);
        Files.writeString(root.resolve("new-source.txt"), "changed\n");
        ReportSession.begin(data, root);
        assertEquals(before, ReportSession.source(data));
        ReportGeneration generation = new ReportGeneration(before, project(root), List.of("Partial reactor"));
        assertTrue(generation.assemble(List.of(module())).draftReasons().contains("Partial reactor"));
        assertThrows(
            IllegalStateException.class, () -> generation.write(List.of(module()), Optional.of("target/custom"))
        );
    }

    @Test
    void derivesTheRootDestinationAndPolicyFromRealMavenModels() {
        Path root = this.repository();
        MavenProject model = InputProjects.project(root);
        model.getProperties().setProperty("airness.version", "1.0.7-SNAPSHOT");
        model.getProperties().setProperty("dependency-check-maven.version", "13.0.0");
        model.getProperties().setProperty("cyclonedx-maven-plugin.version", "2.9.3");
        Plugin license = InputProjects.plugin(
            model, "license-maven-plugin", """
                <configuration><includedLicenses><includedLicense>MIT</includedLicense></includedLicenses>
                <licenseMerges><licenseMerge>MIT|MIT License</licenseMerge></licenseMerges></configuration>
                """
        );
        license.setGroupId("org.codehaus.mojo");
        ReportProject project = ReportProject.from(model, model);
        assertEquals(root, project.root());
        assertEquals(root.resolve("target"), project.build());
        assertEquals("MIT", project.policy().normalized("MIT License"));
    }

    @SneakyThrows
    private Path repository() {
        Path root = this.directory.toRealPath();
        Files.writeString(root.resolve(".gitignore"), "target/\n");
        git(root, List.of("init", "--quiet"));
        git(root, List.of("config", "user.name", "Report fixture"));
        git(root, List.of("config", "user.email", "fixture@example.invalid"));
        git(root, List.of("add", ".gitignore"));
        git(
            root, List.of(
                "-c", "commit.gpgsign=false", "-c", "core.hooksPath=/dev/null", "commit", "--quiet",
                "-m", "test(report): record report fixture sources"
            )
        );
        return root;
    }

    @SneakyThrows
    private static void git(Path root, List<String> arguments) {
        List<String> command = Stream.concat(Stream.of("git", "-C", root.toString()), arguments.stream()).toList();
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        if (!process.waitFor(Duration.ofSeconds(GIT_TIMEOUT)) || process.exitValue() != 0) {
            process.destroyForcibly();
            throw new IllegalStateException("Fixture Git command failed: " + arguments);
        }
    }

    private static ReportProject project(Path root) {
        return new ReportProject(
            root, root.resolve("target"), new ReportIdentity("fixture", Map.of()), new ReportLicensePolicy(
                Set.of("MIT"), Map.of()
            )
        );
    }

    private static ReportModule module() {
        return new ReportModule(
            "example:root:pom:1", List.of(), List.of(), Map.of(
                "SBOM SHA-256", ReportJson.hash("{}"),
                "Scanner SHA-256", ReportJson.hash("scanner"), "Scan date", "2026-10-13T12:00:00Z", "Graph", "{}"
            )
        );
    }
}
