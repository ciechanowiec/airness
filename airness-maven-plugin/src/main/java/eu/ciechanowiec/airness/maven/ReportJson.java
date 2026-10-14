package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Strict JSON evidence reading and stable content hashes.
 */
@UtilityClass
final class ReportJson {

    @SneakyThrows
    static JsonNode read(Path file) {
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
            throw new IllegalStateException("Missing or unsafe compliance evidence: " + file);
        }
        JsonNode document = Optional.ofNullable(new ObjectMapper().readTree(file.toFile()))
            .orElseThrow(() -> new IllegalStateException("Empty compliance evidence: " + file));
        if (!document.isObject()) {
            throw new IllegalStateException("Malformed compliance evidence: " + file);
        }
        return document;
    }

    @SneakyThrows
    static String encode(Object value) {
        return new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(value)
            .replace("<", "\\u003c").replace(">", "\\u003e")
            .replace("&", "\\u0026");
    }

    @SneakyThrows
    static void write(Path file, Object value) {
        Files.createDirectories(file.getParent());
        Files.writeString(file, encode(value));
    }

    @SneakyThrows
    static String hash(String text) {
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))
        );
    }

    static String required(JsonNode object, String name) {
        JsonNode value = object.path(name);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalStateException("Compliance evidence has no " + name);
        }
        return value.asText();
    }
}
