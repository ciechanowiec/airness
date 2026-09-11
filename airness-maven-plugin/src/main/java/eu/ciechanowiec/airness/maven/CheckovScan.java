package eu.ciechanowiec.airness.maven;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Runs the local IaC policy without platform downloads, native overrides, or external code execution.
 */
@UtilityClass
final class CheckovScan {

    static List<String> arguments(String framework) throws IOException {
        List<String> arguments = new ArrayList<>(
            List.of(
                "--directory", "/input/workspace/airness/project", "--config-file", "/output/empty.yaml",
                "--skip-download",
                "--skip-results-upload", "--download-external-modules", "false", "--evaluate-variables", "true",
                "--compact", "--output", "json", "--skip-check", CheckovPolicy.exclusions(), "--framework", framework
            )
        );
        return List.copyOf(arguments);
    }

    static Path input(ScannerTree tree) {
        return tree.input().resolve("workspace/airness/project");
    }
}
