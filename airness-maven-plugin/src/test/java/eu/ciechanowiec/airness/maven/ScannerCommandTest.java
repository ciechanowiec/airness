package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScannerCommandTest {

    @Test
    @SneakyThrows
    void retainsARealProcessOutputAndRejectsItsOperationalFailure(@TempDir Path directory) {
        Path output = directory.resolve("git.txt");
        ScannerCommand.run(List.of("git", "--version"), output);
        assertTrue(Files.readString(output).startsWith("git version"));
        assertThrows(IOException.class, () -> ScannerCommand.run(List.of("git", "--airness-invalid-option"), output));
    }

    @Test
    @SneakyThrows
    void boundsAProcessThatCannotFinishWithinItsDeadline(@TempDir Path directory) {
        Process process = new ProcessBuilder("sleep", "10").start();
        assertThrows(IOException.class, () -> ScannerCommand.await(process, directory, Duration.ofMillis(1)));
        assertTrue(process.waitFor(Duration.ofSeconds(1)));
    }

    @Test
    @SneakyThrows
    void preservesInterruptionWhileTerminatingTheProcess(@TempDir Path directory) {
        Process process = new ProcessBuilder("sleep", "10").start();
        Thread.currentThread().interrupt();
        try {
            assertThrows(IOException.class, () -> ScannerCommand.await(process, directory, Duration.ofSeconds(1)));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            assertTrue(Thread.interrupted());
        }
        assertTrue(process.waitFor(Duration.ofSeconds(1)));
    }
}
