package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DockerCheckProbeTest {

    private static final String IMAGE = "scanner:test";

    @Test
    void mountsTheRepositoryReadOnlyAndReadsItWithThePosixShell(@TempDir Path directory) {
        List<String> command = AbstractDockerCheckMojo.probeCommand(directory, IMAGE);
        int entrypoint = command.indexOf("--entrypoint");

        assertTrue(command.contains(directory + ":/repo:ro"), "the same mount the check takes, read-only");
        assertEquals("/bin/sh", command.get(entrypoint + 1), "the image entrypoint is replaced by a shell");
        assertEquals(IMAGE, command.get(entrypoint + 2), "the image the check itself runs");
    }

    @Test
    void readsTheListingTheGitPointerAndTheTopLevelFiles(@TempDir Path directory) {
        String probe = AbstractDockerCheckMojo.probeCommand(directory, IMAGE).getLast();

        assertTrue(probe.contains("ls -A ."), "the directory is listed");
        assertTrue(probe.contains("cat .git/HEAD") && probe.contains("else cat .git"), "a worktree .git file counts");
        assertTrue(probe.contains("-maxdepth 1 -type f"), "every top-level file is opened");
    }
}
