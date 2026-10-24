package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Reads the JSON report actionlint writes when asked for {@code -format '{{json .}}'}.
 *
 * <p>A clean run writes an empty array. A finding without a file, a line or a message is refused rather
 * than read as clean.
 */
@UtilityClass
final class ActionlintReport {

    /**
     * The JSON template actionlint renders its findings with.
     */
    static final String FORMAT = "{{json .}}";

    /**
     * The findings of one report.
     *
     * @param report the report actionlint wrote
     * @return one line per finding, as path, line, kind and message
     * @throws IOException when the report is missing or malformed
     */
    static List<String> read(Path report) throws IOException {
        JsonNode root = ScannerJson.read(report);
        if (!root.isArray()) {
            throw new IOException("Actionlint evidence must be an array: " + report);
        }
        List<String> findings = new ArrayList<>();
        for (JsonNode finding : root) {
            findings.add(
                "%s:%d [%s] %s".formatted(
                    ScannerJson.text(finding, "filepath"), ScannerJson.integer(finding, "line"),
                    ScannerJson.text(finding, "kind"), ScannerJson.text(finding, "message")
                )
            );
        }
        return List.copyOf(findings);
    }
}
