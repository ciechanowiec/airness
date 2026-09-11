package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScannerJsonTest {

    @Test
    @SneakyThrows
    void rejectsMissingMalformedAndScalarReports(@TempDir Path directory) {
        Path report = directory.resolve("report.json");
        assertThrows(IOException.class, () -> ScannerJson.read(report));
        for (String text : new String[] {"", "null", "true", "broken"}) {
            Files.writeString(report, text);
            assertThrows(IOException.class, () -> ScannerJson.read(report), text);
        }
    }

    @Test
    @SneakyThrows
    void requiresTheDeclaredFieldTypes(@TempDir Path directory) {
        Path report = Files.writeString(directory.resolve("report.json"), "{\"name\":\"scanner\",\"items\":[]}");
        JsonNode root = ScannerJson.read(report);
        assertEquals("scanner", ScannerJson.text(root, "name"));
        assertEquals(0, ScannerJson.array(root, "items").size());
        assertThrows(IOException.class, () -> ScannerJson.array(root, "name"));
        assertThrows(IOException.class, () -> ScannerJson.text(root, "items"));
    }

    @Test
    @SneakyThrows
    void requiresTheExitStatusToAgreeWithTheReport(@TempDir Path directory) {
        ScannerJson.exit(0, false, directory);
        ScannerJson.exit(1, true, directory);
        assertThrows(IOException.class, () -> ScannerJson.exit(0, true, directory));
        assertThrows(IOException.class, () -> ScannerJson.exit(2, false, directory));
    }

    @Test
    @SneakyThrows
    void rejectsStringAndNegativeCounters(@TempDir Path directory) {
        Path report = Files.writeString(
            directory.resolve("report.json"), "{\"count\":0,\"text\":\"0\",\"negative\":-1}"
        );
        JsonNode root = ScannerJson.read(report);
        assertEquals(0, ScannerJson.integer(root, "count"));
        assertThrows(IOException.class, () -> ScannerJson.integer(root, "text"));
        assertThrows(IOException.class, () -> ScannerJson.integer(root, "negative"));
    }
}
