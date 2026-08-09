package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Exercises process failures that repository-level checks must surface rather than reinterpret.
 */
class GitPlumbingTest {

    @Test
    void reportsANonzeroGitExit() {
        Path root = new GitFixture("git-plumbing-exit").root();
        IllegalStateException thrown = assertThrows(
            IllegalStateException.class,
            () -> GitPlumbing.run(root, List.of("rev-parse", "--verify", "missing-ref"))
        );
        assertTrue(thrown.toString().contains("exited with code"));
    }

    @Test
    void reportsAWorkingDirectoryThatDoesNotExist() {
        Path missing = Path.of("target", "missing-git-working-directory").toAbsolutePath();
        assertThrows(
            UncheckedIOException.class,
            () -> GitPlumbing.run(missing, List.of("rev-parse", "--show-toplevel"))
        );
    }

    @Test
    void preservesAnInterruptWhileWaitingForGit() {
        Path root = new GitFixture("git-plumbing-interrupt").root();
        Thread.currentThread().interrupt();
        try {
            assertThrows(
                IllegalStateException.class,
                () -> GitPlumbing.run(root, List.of("rev-parse", "--show-toplevel"))
            );
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            assertTrue(Thread.interrupted(), "reading the flag clears it for the remaining test worker");
        }
    }
}
