package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScannerProcessTest {

    private static final String IMAGE = "example/scanner:1@sha256:" + "a".repeat(64);

    @Test
    void isolatesInputsAndKeepsHostPolicyOutOfTheContainer(@TempDir Path directory) {
        ScannerTree tree = new ScannerTree(directory.resolve("output"), directory.resolve("input"));
        List<String> command = new ScannerProcess(ScannerTools.image("shellcheck"), tree)
            .command("shellcheck", List.of("--version"));
        assertTrue(command.contains("none"));
        assertTrue(command.contains("type=bind,source=" + tree.input() + ",target=/input,readonly"));
        assertFalse(command.contains("/var/run/docker.sock"));
        assertFalse(command.contains("--env-file"));
        assertTrue(new ScannerProcess(ScannerTools.image("shellcheck"), tree).probeCommand().contains("/bin/sh"));
    }

    @Test
    void rejectsUnownedAndUnpinnedImages(@TempDir Path directory) {
        ScannerTree tree = new ScannerTree(directory, directory.resolve("input"));
        assertThrows(
            IllegalArgumentException.class,
            () -> new ScannerProcess(IMAGE, tree).command("checkov", List.of())
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new ScannerProcess("example/scanner:latest", tree).command("checkov", List.of())
        );
    }
}
