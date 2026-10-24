package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Reads the JSON report hadolint writes for one Dockerfile it was given on its standard input.
 *
 * <p>Hadolint names a file read from its standard input {@code -}, so every finding is attributed to the
 * Dockerfile the caller fed it. A finding without a rule or a line is refused rather than read as clean.
 * A style suggestion lies below the info threshold the check runs at, so it is not a finding.
 */
@UtilityClass
final class HadolintReport {

    private static final String STYLE = "style";

    /**
     * The findings of one report.
     *
     * @param report     the report hadolint wrote
     * @param dockerfile the repository path of the Dockerfile it read
     * @return one line per finding, as path, line, rule, level and message
     * @throws IOException when the report is missing or malformed
     */
    static List<String> read(Path report, String dockerfile) throws IOException {
        JsonNode root = ScannerJson.read(report);
        if (!root.isArray()) {
            throw new IOException("Hadolint evidence must be an array: " + report);
        }
        List<String> findings = new ArrayList<>();
        for (JsonNode finding : root) {
            String level = ScannerJson.text(finding, "level");
            if (!STYLE.equals(level)) {
                findings.add(
                    "%s:%d %s %s: %s".formatted(
                        dockerfile, ScannerJson.integer(finding, "line"), ScannerJson.text(finding, "code"), level,
                        ScannerJson.text(finding, "message")
                    )
                );
            }
        }
        return List.copyOf(findings);
    }
}
