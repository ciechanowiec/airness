package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecretScanMojoTest {

    private static final String IMAGE = "scanner:test";

    @Test
    void disablesInlineSecretExemptions(
        @TempDir Path directory
    ) {
        List<String> command = SecretScanMojo.dockerCommand(directory, IMAGE);

        assertEquals(
            1, Collections.frequency(command, "--ignore-gitleaks-allow"),
            "inline comments must not bypass the governed exception configuration"
        );
    }

    @Test
    void selectsTheGovernedExceptionConfiguration(
        @TempDir Path directory
    ) {
        List<String> command = SecretScanMojo.dockerCommand(directory, IMAGE);

        assertEquals(
            "/repo/.gitleaks.toml", command.get(command.indexOf("--config") + 1),
            "documented fixture exceptions still come from the repository configuration"
        );
    }

    @Test
    void keepsCredentialsOutOfScannerOutput(
        @TempDir Path directory
    ) {
        List<String> command = SecretScanMojo.dockerCommand(directory, IMAGE);

        assertTrue(command.contains("--redact"), "reported findings must not expose the credential");
    }

    @Test
    void mountsTheRepositoryReadOnly(
        @TempDir Path directory
    ) {
        List<String> command = SecretScanMojo.dockerCommand(directory, IMAGE);

        assertEquals(
            directory + ":/repo:ro", command.get(command.indexOf("-v") + 1),
            "the scanner must not change the repository it verifies"
        );
    }
}
