package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * ShellCheck's compatible default and optional checks, with no ambient configuration.
 */
@UtilityClass
final class ShellcheckScan {

    private static final List<String> OPTIONAL = List.of(
        "add-default-case", "avoid-negated-conditions", "avoid-nullary-conditions",
        "check-extra-masked-returns", "check-set-e-suppressed", "deprecate-which",
        "quote-safe-variables", "require-double-brackets", "require-variable-braces", "useless-use-of-cat"
    );

    static List<String> options() {
        return List.of(
            "--norc", "--format=json1", "--severity=style", "--external-sources", "--check-sourced",
            "--source-path=/input", "--source-path=SCRIPTDIR",
            "--enable=" + String.join(",", OPTIONAL), "--"
        );
    }

    static List<String> read(Path report) throws IOException {
        JsonNode root = ScannerJson.read(report);
        List<String> findings = new ArrayList<>();
        for (JsonNode comment : ScannerJson.array(root, "comments")) {
            String file = ScannerJson.text(comment, "file");
            String message = ScannerJson.text(comment, "message");
            JsonNode code = comment.path("code");
            JsonNode line = comment.path("line");
            if (!code.isIntegralNumber() || !line.isIntegralNumber()) {
                throw new IOException("ShellCheck finding has no rule or location: " + report);
            }
            findings.add("%s:%d SC%d: %s".formatted(file, line.asInt(), code.asInt(), message));
        }
        return findings.stream().distinct().toList();
    }
}
