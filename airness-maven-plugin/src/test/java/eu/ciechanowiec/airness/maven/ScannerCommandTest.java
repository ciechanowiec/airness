package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScannerCommandTest {

    private static final Duration AMPLE = Duration.ofSeconds(30);
    private static final Duration BRIEF = Duration.ofMillis(100);

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
    void feedsAFileToTheStandardInputAndRunsFromADirectory(@TempDir Path directory) {
        Path input = Files.writeString(directory.resolve("input.txt"), "piped content\n");
        Path output = directory.resolve("cat.txt");
        assertEquals(0, ScannerCommand.executeWithInput(List.of("cat"), input, output));
        assertEquals("piped content\n", Files.readString(output));
        Path listing = directory.resolve("ls.txt");
        assertEquals(0, ScannerCommand.executeIn(List.of("ls"), directory, listing));
        assertTrue(Files.readString(listing).contains("input.txt"));
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
    void runsAQuietCommandToItsExitCode() {
        assertEquals(0, ScannerCommand.quiet(List.of("git", "--version"), AMPLE));
        assertTrue(ScannerCommand.quiet(List.of("git", "--airness-invalid-option"), AMPLE) > 0);
    }

    @Test
    void stopsAQuietCommandThatDoesNotAnswerWithinItsLimit() {
        TimeoutException timeout = assertThrows(
            TimeoutException.class, () -> ScannerCommand.quiet(List.of("sleep", "10"), BRIEF)
        );
        assertTrue(
            Objects.requireNonNull(timeout.getMessage()).startsWith("sleep 10 did not finish"), timeout.getMessage()
        );
    }

    @Test
    void preservesInterruptionWhileStoppingAQuietCommand() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(IOException.class, () -> ScannerCommand.quiet(List.of("sleep", "10"), Duration.ofSeconds(5)));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            assertTrue(Thread.interrupted());
        }
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
