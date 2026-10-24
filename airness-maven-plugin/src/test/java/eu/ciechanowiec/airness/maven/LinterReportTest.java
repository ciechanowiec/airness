package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies how the hadolint and actionlint reports become findings, and that a malformed one never reads
 * as clean.
 */
class LinterReportTest {

    @Test
    @SneakyThrows
    void attributesEachHadolintFindingToTheDockerfileItRead(@TempDir Path directory) {
        Path report = Files.writeString(
            directory.resolve("hadolint.json"), """
                [{"code":"DL3018","column":1,"file":"-","level":"warning","line":2,"message":"Pin versions"},
                 {"code":"DL3059","column":1,"file":"-","level":"style","line":3,"message":"Consolidate RUN"}]
                """
        );
        assertEquals(
            List.of("images/api.Dockerfile:2 DL3018 warning: Pin versions"),
            HadolintReport.read(report, "images/api.Dockerfile"),
            "the standard-input name is replaced by the repository path, and a style suggestion is below the threshold"
        );
    }

    @Test
    @SneakyThrows
    void namesEachActionlintFindingWithItsKind(@TempDir Path directory) {
        Path report = Files.writeString(
            directory.resolve("actionlint.json"), """
                [{"message":"property \\"foo\\" is not defined","filepath":".github/workflows/ci.yml","line":6,
                  "column":23,"kind":"expression"}]
                """
        );
        assertEquals(
            List.of(".github/workflows/ci.yml:6 [expression] property \"foo\" is not defined"),
            ActionlintReport.read(report),
            "the file, line, kind and message are kept"
        );
    }

    @Test
    @SneakyThrows
    void refusesAnIncompleteOrMisshapenReport(@TempDir Path directory) {
        Path report = Files.writeString(directory.resolve("report.json"), "[{\"code\":\"DL3006\"}]");
        assertThrows(IOException.class, () -> HadolintReport.read(report, "Dockerfile"), "no line, no finding");
        Files.writeString(report, "{\"comments\":[]}");
        assertThrows(IOException.class, () -> ActionlintReport.read(report), "an object is not the report shape");
        assertThrows(IOException.class, () -> HadolintReport.read(report, "Dockerfile"), "nor is it for hadolint");
        Files.writeString(report, "[]");
        assertEquals(List.of(), ActionlintReport.read(report), "an empty array is a clean run");
    }
}
