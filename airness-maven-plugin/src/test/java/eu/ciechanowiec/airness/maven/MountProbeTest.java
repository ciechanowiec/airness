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

    @Test
    @SneakyThrows
    void readsTheHistoryOverTheSameMountsTheCheckTakes(@TempDir Path directory) {
        HistoryMount mount = HistoryMount.of(directory);
        List<String> command = MountProbe.historyCommand(mount, IMAGE, true);
        int entrypoint = command.indexOf("--entrypoint");

        assertEquals(mount.volumes(), command.subList(3, entrypoint), "the mounts the scan itself takes");
        assertEquals(IMAGE, command.get(entrypoint + 2), "the Git of the image the check itself runs");
        assertEquals(List.of("/repo", "commits"), command.subList(command.size() - 2, command.size()));
    }

    @Test
    @SneakyThrows
    void tellsTheProbeWhenHeadNamesNoCommitYet(@TempDir Path directory) {
        List<String> command = MountProbe.historyCommand(HistoryMount.of(directory), IMAGE, false);

        assertEquals("unborn", command.getLast(), "an unborn HEAD is only asked to be found");
    }

    @Test
    void readsAMountedInputAndNamesItWhenItArrivesEmpty(@TempDir Path directory) {
        MountProbe.Probe probe = MountProbe.input(directory, IMAGE);

        assertEquals(MountProbe.inputCommand(directory, IMAGE), probe.command(), "the input probe of the image");
        assertEquals(directory, probe.mount(), "the directory a failed start names");
        assertEquals(MountProbe.emptyInput(directory), probe.unreadable(), "the fix for an unshared folder");
    }

    @Test
    void keepsTheCommandAProbeWasGiven(@TempDir Path directory) {
        List<String> command = new ArrayList<>(List.of("docker", "run"));
        MountProbe.Probe probe = new MountProbe.Probe(command, directory, "unreadable");
        command.add("changed");

        assertEquals(List.of("docker", "run"), probe.command(), "the probe runs what it was built with");
    }

    @Test
    @SneakyThrows
    void acceptsAHistoryGitCanRead(@TempDir Path directory) {
        Path root = committed(directory.resolve("clone"));

        assertEquals(0, historyExit(root, "commits"), "a clone whose commits Git reads");
    }

    @Test
    @SneakyThrows
    void acceptsAnUnbornHeadAndRejectsAHistoryThatShouldBeThere(@TempDir Path directory) {
        Path root = Files.createDirectory(directory.resolve("unborn"));
        git(root, "init", "--quiet");

        assertEquals(0, historyExit(root, "unborn"), "no commit yet is nothing to scan, not a failure");
        assertTrue(historyExit(root, "commits") != 0, "a HEAD the host saw a commit behind must resolve");
    }

    @Test
    @SneakyThrows
    void rejectsAWorktreeWhoseCloneCannotBeReached(@TempDir Path directory) {
        Path clone = committed(directory.resolve("clone"));
        Path worktree = directory.resolve("worktree");
        git(clone, "worktree", "add", "--quiet", "--detach", worktree.toString());
        Files.move(clone, directory.resolve("unmounted"));

        assertTrue(historyExit(worktree, "commits") != 0, "a pointer to nowhere is no history");
    }

    @Test
    void namesTheRepositoryAndTheWorktreeWhenItsHistoryIsUnreadable(@TempDir Path directory) {
        String message = MountProbe.unreadableHistory(directory);

        assertTrue(message.contains(directory.toString()), "the repository whose history was not read");
        assertTrue(message.contains("clean history"), "what the scan would have reported");
        assertTrue(message.contains("git worktree add"), "the layout that needs the clone mounted beside it");
    }

    /**
     * Runs the history probe on the host against a working tree, as the container runs it against the mount.
     */
    @SneakyThrows
    private static int historyExit(Path workingTree, String head) {
        List<String> command = MountProbe.historyCommand(new HistoryMount(List.of(), "/repo", List.of()), IMAGE, true);
        List<String> local = new ArrayList<>(List.of("sh"));
        local.addAll(command.subList(command.indexOf("-c"), command.size() - 2));
        local.addAll(List.of(workingTree.toString(), head));
        return new ProcessBuilder(local)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
            .waitFor();
    }

    @SneakyThrows
    private static Path committed(Path root) {
        Files.createDirectories(root);
        git(root, "init", "--quiet");
        Files.writeString(root.resolve("file.txt"), "content\n");
        git(root, "add", ".");
        git(
            root, "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "-c", "commit.gpgsign=false",
            "commit", "--quiet", "--no-verify", "--message", "test: record a fixture"
        );
        return root;
    }

    @SneakyThrows
    private static void git(Path root, String... arguments) {
        List<String> command = new ArrayList<>(List.of("git", "-C", root.toString()));
        command.addAll(List.of(arguments));
        assertEquals(
            0, new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start().waitFor(),
            () -> String.join(" ", command)
        );
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
