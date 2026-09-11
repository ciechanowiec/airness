package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShellcheckScanTest {

    @Test
    @SneakyThrows
    void namesTheRuleAndOriginalScriptLocation(@TempDir Path directory) {
        Path report = Files.writeString(
            directory.resolve("report.json"), """
                {"comments":[{"file":"/input/test.sh","line":2,"code":2086,"message":"Quote the variable"}]}
                """
        );
        assertEquals(List.of("/input/test.sh:2 SC2086: Quote the variable"), ShellcheckScan.read(report));
    }

    @Test
    @SneakyThrows
    void rejectsReportsWithoutACompleteFinding(@TempDir Path directory) {
        Path report = Files.writeString(
            directory.resolve("report.json"), """
                {"comments":[{"file":"test.sh","message":"Finding"}]}
                """
        );
        assertThrows(IOException.class, () -> ShellcheckScan.read(report));
        Files.writeString(report, "{\"comments\":[]}");
        assertEquals(List.of(), ShellcheckScan.read(report));
    }

    @Test
    void controlsConfigurationSeverityAndSourcedFileCoverage() {
        List<String> options = ShellcheckScan.options();
        assertTrue(options.containsAll(List.of("--norc", "--severity=style", "--check-sourced", "--external-sources")));
        assertEquals("--", options.getLast());
        assertTrue(options.stream().anyMatch(option -> option.contains("check-set-e-suppressed")));
    }
}
