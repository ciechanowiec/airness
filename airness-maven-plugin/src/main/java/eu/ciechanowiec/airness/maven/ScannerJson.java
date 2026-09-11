package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import lombok.experimental.UtilityClass;

/**
 * Strict machine-readable scanner evidence. an absent field is never an empty successful result.
 */
@UtilityClass
final class ScannerJson {

    static JsonNode read(Path report) throws IOException {
        return Optional.ofNullable(new ObjectMapper().readTree(report.toFile()))
            .filter(node -> node.isObject() || node.isArray())
            .orElseThrow(() -> new IOException("Missing structured scanner evidence: " + report));
    }

    static JsonNode array(JsonNode node, String field) throws IOException {
        JsonNode value = node.path(field);
        if (!value.isArray()) {
            throw new IOException("Scanner evidence requires array " + field);
        }
        return value;
    }

    static String text(JsonNode node, String field) throws IOException {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IOException("Scanner evidence requires text " + field);
        }
        return value.asText();
    }

    static int integer(JsonNode node, String field) throws IOException {
        JsonNode value = node.path(field);
        if (!value.isInt() || value.asInt() < 0) {
            throw new IOException("Scanner evidence requires a nonnegative integer " + field);
        }
        return value.asInt();
    }

    static void exit(int exit, boolean findings, Path report) throws IOException {
        int expected = findings ? 1 : 0;
        if (exit != expected) {
            throw new IOException("Scanner exit " + exit + " disagrees with evidence at " + report);
        }
    }
}
