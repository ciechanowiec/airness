package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckovReportTest {

    private static final String CLEAN = """
        {"check_type":"dockerfile","summary":{"checkov_version":"3.3.17","passed":0,"failed":0,
          "skipped":0,"parsing_errors":0},"results":{"passed_checks":[],"failed_checks":[],
          "skipped_checks":[],"parsing_errors":[]}}
        """;

    @Test
    @SneakyThrows
    void acceptsTheNativeZeroSummaryOnlyWithAnExplicitFrameworkAndCompleteZeroCounts(@TempDir Path directory) {
        String summary = """
            {"passed":0,"failed":0,"skipped":0,"parsing_errors":0,"resource_count":0,"checkov_version":"3.3.17"}
            """;
        Path report = Files.writeString(directory.resolve("report.json"), summary);
        assertEquals(Set.of("kubernetes"), CheckovReport.read(report, "kubernetes").frameworks());
        assertThrows(IOException.class, () -> CheckovReport.read(report));
        Files.writeString(report, summary.replace("\"failed\":0", "\"failed\":1"));
        assertThrows(IOException.class, () -> CheckovReport.read(report, "kubernetes"));
    }

    @Test
    @SneakyThrows
    void acceptsBothSingleAndMultipleFrameworkReports(@TempDir Path directory) {
        Path report = Files.writeString(directory.resolve("report.json"), CLEAN);
        assertEquals(new CheckovReport(List.of(), Set.of(), Set.of("dockerfile")), CheckovReport.read(report));
        Files.writeString(report, '[' + CLEAN + ']');
        assertEquals(new CheckovReport(List.of(), Set.of(), Set.of("dockerfile")), CheckovReport.read(report));
    }

    @Test
    @SneakyThrows
    void rejectsIncompleteAndUnexpectedFrameworkEvidence(@TempDir Path directory) {
        Path report = directory.resolve("report.json");
        for (
            String malformed : List.of(
                CLEAN.replace("dockerfile", "secrets"), CLEAN.replace("3.3.17", "0.0.0"),
                CLEAN.replace("\"skipped_checks\":[]", "\"skipped_checks\":[{}]"),
                CLEAN.replace("\"skipped\":0", "\"skipped\":1"),
                CLEAN.replace("\"passed\":0", "\"passed\":1")
            )
        ) {
            Files.writeString(report, malformed);
            assertThrows(IOException.class, () -> CheckovReport.read(report), malformed);
        }
    }

    @Test
    @SneakyThrows
    void reportsFailedRulesAndTheFilesActuallyEvaluated(@TempDir Path directory) {
        String finding = """
            {"check_id":"CKV_DOCKER_8","check_name":"Do not run as root",
             "file_path":"/Dockerfile","resource":"/Dockerfile.USER"}
            """;
        String content = CLEAN.replace("\"failed\":0", "\"failed\":1")
            .replace("\"failed_checks\":[]", "\"failed_checks\":[" + finding + ']');
        Path report = Files.writeString(directory.resolve("report.json"), content);
        CheckovReport result = CheckovReport.read(report);
        assertEquals(Set.of("/Dockerfile"), result.files());
        assertEquals(List.of("/Dockerfile CKV_DOCKER_8 (/Dockerfile.USER): Do not run as root"), result.findings());
    }
}
