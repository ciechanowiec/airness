package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * Reads the inventory exported directly by the license check after its policy evaluation.
 */
@UtilityClass
final class ReportLicenses {

    static Map<String, List<String>> read(Path file, Instant started) {
        JsonNode document = ReportJson.read(file);
        current(ReportJson.required(document, "generatedAt"), started);
        JsonNode packages = document.path("packages");
        if (!packages.isArray()) {
            throw new IllegalStateException("License evidence has no package inventory: " + file);
        }
        return packages.valueStream().collect(
            Collectors.toUnmodifiableMap(
                ReportLicenses::coordinate,
                ReportLicenses::licenses
            )
        );
    }

    static void current(String timestamp, Instant started) {
        Instant generated = OffsetDateTime.parse(timestamp).toInstant();
        if (generated.isBefore(started.truncatedTo(ChronoUnit.SECONDS))) {
            throw new IllegalStateException("Compliance evidence predates this Maven invocation; regenerate it");
        }
    }

    private static String coordinate(JsonNode entry) {
        return ReportJson.required(entry, "group") + ':' + ReportJson.required(entry, "artifact") + ':'
            + ReportJson.required(entry, "version");
    }

    private static List<String> licenses(JsonNode entry) {
        JsonNode declared = entry.path("declared");
        if (!declared.isArray()) {
            throw new IllegalStateException("License evidence has no supplier declarations");
        }
        return declared.valueStream().map(JsonNode::asText).toList();
    }
}
