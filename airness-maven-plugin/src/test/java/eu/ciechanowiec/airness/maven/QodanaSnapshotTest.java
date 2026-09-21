package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QodanaSnapshotTest {

    private static final String OUTPUT = "output";
    private static final String TRACKED = "tracked.txt";

    @Test
    @SneakyThrows
    void excludesIgnoredTreesBeforeCopyingAndHonorsNestedNegation(@TempDir Path directory) {
        Path root = repository(directory);
        write(root, ".gitignore", "target/\nlogs/\n*.local\n");
        write(root, "nested/.gitignore", "*.txt\n!keep.txt\n");
        List<String> ignored = List.of(
            "target/deep/build.bin", "logs/run/result", "nested/target/build.bin",
            "nested/drop.txt", "scratch.local"
        );
        writeIgnoredFiles(root, ignored);
        write(root, "nested/keep.txt", "negated\n");
        write(root, "new.txt", "untracked\n");
        Path manifest = QodanaSnapshot.prepare(root, directory.resolve(OUTPUT));
        assertEquals(0, copy(root, manifest));
        Path destination = directory.resolve("copy");
        assertEquals("negated\n", Files.readString(destination.resolve("nested/keep.txt")));
        assertEquals("untracked\n", Files.readString(destination.resolve("new.txt")));
        for (String path : ignored) {
            assertFalse(Files.exists(destination.resolve(path)), path);
        }
        assertFalse(Files.exists(destination.resolve("target")));
        assertFalse(Files.exists(destination.resolve("logs")));
    }

    @Test
    @SneakyThrows
    void preservesCurrentTrackedBytesEvenWhenIgnoredAndDeletedWorkingFilesStayAbsent(@TempDir Path directory) {
        Path root = repository(directory);
        write(root, TRACKED, "committed\n");
        write(root, "target/tracked.txt", "tracked under ignored directory\n");
        write(root, "deleted.txt", "deleted later\n");
        git(root, "add", ".");
        git(root, "commit", "--quiet", "--message", "test: record the original source contents");
        write(root, TRACKED, "staged\n");
        git(root, "add", TRACKED);
        write(root, TRACKED, "current working bytes\n");
        write(root, ".gitignore", "*.txt\ntarget/\n");
        write(root, "target/ignored.txt", "ignored sibling\n");
        Files.delete(root.resolve("deleted.txt"));
        Path manifest = QodanaSnapshot.prepare(root, directory.resolve(OUTPUT));
        assertEquals(0, copy(root, manifest));
        Path destination = directory.resolve("copy");
        assertEquals("current working bytes\n", Files.readString(destination.resolve(TRACKED)));
        assertEquals("tracked under ignored directory\n", Files.readString(destination.resolve("target/tracked.txt")));
        assertFalse(Files.exists(destination.resolve("target/ignored.txt")));
        assertFalse(Files.exists(destination.resolve("deleted.txt")));
        assertArrayEquals(
            Files.readAllBytes(root.resolve(".git/index")), Files.readAllBytes(destination.resolve(".git/index"))
        );
        git(destination, "show", "HEAD:tracked.txt");
        assertEquals("committed\n", Files.readString(directory.resolve("git.txt")));
        write(destination, TRACKED, "copy changes\n");
        assertEquals("current working bytes\n", Files.readString(root.resolve(TRACKED)));
    }

    @Test
    @SneakyThrows
    void copiesLiteralNamesBinaryBytesAndSymbolicLinks(@TempDir Path directory) {
        Path root = repository(directory);
        List<String> names = List.of("with space", "line\nbreak", "tab\tname", "-C", "back\\slash", "quote'\"name");
        byte[] bytes = {0, 1, -1};
        writeBinaryFiles(root, names, bytes);
        Path missingTarget = Path.of("missing");
        Path ignoredTarget = Path.of("ignored");
        Files.createSymbolicLink(root.resolve("dangling"), missingTarget);
        Files.createSymbolicLink(root.resolve("linked-directory"), ignoredTarget);
        write(root, ".gitignore", "ignored/\n");
        write(root, "ignored/secret", "never followed\n");
        Path manifest = QodanaSnapshot.prepare(root, directory.resolve(OUTPUT));
        assertEquals(0, copy(root, manifest));
        Path destination = directory.resolve("copy");
        for (String name : names) {
            assertArrayEquals(bytes, Files.readAllBytes(destination.resolve(name)), name);
        }
        assertEquals(missingTarget, Files.readSymbolicLink(destination.resolve("dangling")));
        assertEquals(ignoredTarget, Files.readSymbolicLink(destination.resolve("linked-directory")));
        assertFalse(Files.exists(destination.resolve("ignored")));
    }

    @Test
    @SneakyThrows
    void copiesTheMetadataOfAnUnbornEmptyRepository(@TempDir Path directory) {
        Path root = repository(directory);
        Path manifest = QodanaSnapshot.prepare(root, directory.resolve(OUTPUT));
        assertEquals(0, copy(root, manifest));
        assertEquals(
            Files.readString(root.resolve(".git/HEAD")),
            Files.readString(directory.resolve("copy/.git/HEAD"))
        );
    }

    @Test
    @SneakyThrows
    void stopsBeforeExtractionWhenASelectedFileDisappears(@TempDir Path directory) {
        Path root = repository(directory);
        write(root, "present.txt", "present\n");
        Path removed = write(root, "removed.txt", "removed after selection\n");
        Path manifest = QodanaSnapshot.prepare(root, directory.resolve(OUTPUT));
        Files.delete(removed);
        assertNotEquals(0, copy(root, manifest));
        assertFalse(Files.exists(directory.resolve("copy/present.txt")));
        assertFalse(Files.exists(directory.resolve("continued")));
    }

    @Test
    @SneakyThrows
    void stopsWhenTheMetadataProducerCannotReadGit(@TempDir Path directory) {
        Path root = repository(directory);
        Path manifest = QodanaSnapshot.prepare(root, directory.resolve(OUTPUT));
        Files.move(root.resolve(".git"), directory.resolve("moved-git"));
        assertNotEquals(0, copy(root, manifest));
        assertFalse(Files.exists(directory.resolve("continued")));
    }

    @Test
    @SneakyThrows
    void stopsWhenExtractionCannotReplaceADirectoryWithAFile(@TempDir Path directory) {
        Path root = repository(directory);
        write(root, "file.txt", "a file\n");
        Path manifest = QodanaSnapshot.prepare(root, directory.resolve(OUTPUT));
        write(directory, "copy/file.txt/child", "blocks extraction\n");
        assertNotEquals(0, copy(root, manifest));
        assertFalse(Files.exists(directory.resolve("continued")));
    }

    @Test
    @SneakyThrows
    void rejectsLinkedWorktreesBeforePreparingAnyCopy(@TempDir Path directory) {
        Path root = repository(directory);
        write(root, TRACKED, "content\n");
        git(root, "add", ".");
        git(root, "commit", "--quiet", "--message", "test: record a standalone repository fixture");
        Path worktree = directory.resolve("worktree");
        git(root, "worktree", "add", "--quiet", "--detach", worktree.toString());
        IOException thrown = assertThrows(
            IOException.class, () -> QodanaSnapshot.prepare(worktree, directory.resolve(OUTPUT))
        );
        assertTrue(thrown.toString().contains("standalone clone"));
        assertFalse(Files.exists(directory.resolve(OUTPUT)));
    }

    @SneakyThrows
    private static void writeBinaryFiles(Path root, Iterable<String> names, byte[] bytes) {
        for (String name : names) {
            Files.write(root.resolve(name), bytes);
        }
    }

    private static void writeIgnoredFiles(Path root, Iterable<String> paths) {
        for (String path : paths) {
            write(root, path, "ignored\n");
        }
    }

    @SneakyThrows
    private static Path repository(Path directory) {
        Path root = Files.createDirectory(directory.resolve("source")).toRealPath();
        git(root, "init", "--quiet");
        git(root, "config", "user.name", "Fixture");
        git(root, "config", "user.email", "fixture@example.invalid");
        return root;
    }

    @SneakyThrows
    private static Path write(Path root, String name, String content) {
        Path file = root.resolve(name);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    @SneakyThrows
    private static void git(Path root, String... arguments) {
        List<String> command = Stream.concat(Stream.of("git", "-C", root.toString()), Stream.of(arguments)).toList();
        Path output = root.resolveSibling("git.txt");
        assertEquals(0, ScannerCommand.execute(command, output), () -> command + ": " + output);
    }

    @SneakyThrows
    private static int copy(Path root, Path manifest) {
        Path directory = root.resolve("..").normalize();
        return ScannerCommand.execute(
            List.of(
                "sh", "-c", QodanaSnapshot.SCRIPT + "touch \"$destination/../continued\"\n", "qodana-snapshot",
                root.toString(), directory.resolve("copy").toString(), manifest.toString()
            ),
            directory.resolve("copy.txt")
        );
    }
}
