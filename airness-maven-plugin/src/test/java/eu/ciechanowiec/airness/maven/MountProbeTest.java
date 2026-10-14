package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MountProbeTest {

    private static final String IMAGE = "scanner:test";

    @Test
    void mountsTheRepositoryReadOnlyAndReadsItWithThePosixShell(@TempDir Path directory) {
        List<String> command = MountProbe.repositoryCommand(directory, IMAGE);
        int entrypoint = command.indexOf("--entrypoint");

        assertTrue(command.contains(directory + ":/repo:ro"), "the same mount the check takes, read-only");
        assertEquals("/bin/sh", command.get(entrypoint + 1), "the image entrypoint is replaced by a shell");
        assertEquals(IMAGE, command.get(entrypoint + 2), "the image the check itself runs");
    }

    @Test
    void readsTheListingTheGitPointerAndTheTopLevelFiles(@TempDir Path directory) {
        String probe = MountProbe.repositoryCommand(directory, IMAGE).getLast();

        assertTrue(probe.contains("ls -A ."), "the directory is listed");
        assertTrue(probe.contains("cat .git/HEAD") && probe.contains("else cat .git"), "a worktree .git file counts");
        assertTrue(probe.contains("-maxdepth 1 -type f"), "every top-level file is opened");
    }

    @Test
    void namesTheRepositoryAndTheFixWhenItIsUnreadable(@TempDir Path directory) {
        String message = MountProbe.unreadableRepository(directory);

        assertTrue(message.contains(directory.toString()), "the path that could not be read");
        assertTrue(message.contains("Files and Folders"), "the macOS privacy grant that fixes it");
    }

    @Test
    void mountsAnInputDirectoryReadOnlyAndHandsItsMountToTheProbe(@TempDir Path directory) {
        List<String> command = MountProbe.inputCommand(directory, IMAGE);
        int entrypoint = command.indexOf("--entrypoint");

        assertTrue(command.contains(directory + ":/input:ro"), "the input is mounted read-only");
        assertEquals(IMAGE, command.get(entrypoint + 2), "the image the check itself runs");
        assertEquals("/input", command.getLast(), "the probe reads the mount, not the host path");
    }

    @Test
    @SneakyThrows
    void rejectsAnInputMountThatArrivesEmpty(@TempDir Path directory) {
        Path unshared = Files.createDirectory(directory.resolve("repository"));

        assertEquals(1, probeExit(unshared), "an unshared folder arrives as an empty directory");
    }

    @Test
    @SneakyThrows
    void acceptsAPopulatedInputMount(@TempDir Path directory) {
        Path repository = Files.createDirectories(directory.resolve("repository/org/example"));
        Files.writeString(repository.resolve("example.pom"), "<project/>");

        assertEquals(0, probeExit(directory.resolve("repository")), "a listed directory with a readable file");
    }

    @Test
    void namesTheInputAndTheFixWhenItArrivesEmpty(@TempDir Path directory) {
        String message = MountProbe.emptyInput(directory);

        assertTrue(message.contains(directory.toString()), "the path that arrived empty");
        assertTrue(message.contains("maven.repo.local"), "the repository setting that moves it");
        assertTrue(message.contains("colima.yaml"), "the shared-folder setting that exposes it");
    }

    /**
     * Runs the probe the container would run, on the host, against the directory the mount would show.
     */
    @SneakyThrows
    private static int probeExit(Path mount) {
        List<String> command = MountProbe.inputCommand(mount, IMAGE);
        List<String> local = new ArrayList<>(List.of("sh"));
        local.addAll(command.subList(command.indexOf("-c"), command.size() - 1));
        local.add(mount.toString());
        return new ProcessBuilder(local)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
            .waitFor();
    }
}
