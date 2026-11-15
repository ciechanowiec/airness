package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The host's processes run with their standard output returned and a file on their standard input, and one that
 * fails is named with what it printed on its standard error. The commands are the shell's own, since what is proven
 * is how a process is run, which Docker's commands share.
 */
class HostDockerTest {

    private static final HostDocker HOST = HostDocker.system();

    @TempDir
    private Path directory;

    @Test
    void returnsWhatACommandPrintedOnItsStandardOutput() {
        assertEquals("listed", HOST.run(List.of("sh", "-c", "printf listed; printf noise >&2"), Optional.empty()));
    }

    @Test
    @SneakyThrows
    void feedsAFileToTheCommandsStandardInput() {
        Path archive = Files.writeString(this.directory.resolve("image.tar"), "saved image");
        assertEquals("saved image", HOST.run(List.of("sh", "-c", "cat"), Optional.of(archive)));
    }

    @Test
    void namesAFailedCommandWithWhatItPrintedOnItsStandardError() {
        IllegalStateException failure = assertThrows(
            IllegalStateException.class, () -> HOST.run(
                List.of("sh", "-c", "echo 'pull access denied' >&2; exit 3"), Optional.empty()
            )
        );
        String message = Objects.requireNonNull(failure.getMessage());
        assertTrue(message.endsWith("exited 3: pull access denied"), message);
    }

    @Test
    void tellsWhetherACommandSucceeded() {
        assertAll(
            () -> assertTrue(HOST.succeeds(List.of("sh", "-c", "exit 0"))),
            () -> assertFalse(HOST.succeeds(List.of("sh", "-c", "exit 1")))
        );
    }
}
