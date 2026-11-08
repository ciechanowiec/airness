package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScanSecretsMojoTest {

    private static final String IMAGE = "scanner:test";

    @Test
    @SneakyThrows
    void disablesInlineSecretExemptions(
        @TempDir Path directory
    ) {
        List<String> command = ScanSecretsMojo.dockerCommand(HistoryMount.of(directory), IMAGE);

        assertEquals(
            1, Collections.frequency(command, "--ignore-gitleaks-allow"),
            "inline comments must not bypass the governed exception configuration"
        );
    }

    @Test
    @SneakyThrows
    void selectsTheGovernedExceptionConfiguration(
        @TempDir Path directory
    ) {
        List<String> command = ScanSecretsMojo.dockerCommand(HistoryMount.of(directory), IMAGE);

        assertEquals(
            "/repo/.gitleaks.toml", command.get(command.indexOf("--config") + 1),
            "documented fixture exceptions still come from the repository configuration"
        );
    }

    @Test
    @SneakyThrows
    void keepsCredentialsOutOfScannerOutput(
        @TempDir Path directory
    ) {
        List<String> command = ScanSecretsMojo.dockerCommand(HistoryMount.of(directory), IMAGE);

        assertTrue(command.contains("--redact"), "reported findings must not expose the credential");
    }

    @Test
    @SneakyThrows
    void mountsTheRepositoryReadOnly(
        @TempDir Path directory
    ) {
        List<String> command = ScanSecretsMojo.dockerCommand(HistoryMount.of(directory), IMAGE);

        assertEquals(
            directory + ":/repo:ro", command.get(command.indexOf("-v") + 1),
            "the scanner must not change the repository it verifies"
        );
    }

    @Test
    @SneakyThrows
    void scansALinkedWorktreeWhereItsPointersLeadAndReadsOnlyIt(@TempDir Path directory) {
        Path clone = Files.createDirectories(directory.resolve("clone")).toRealPath();
        git(clone, "init", "--quiet");
        Files.writeString(clone.resolve("file.txt"), "content\n");
        git(clone, "add", ".");
        git(
            clone, "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "-c", "commit.gpgsign=false",
            "commit", "--quiet", "--no-verify", "--message", "test: record a fixture"
        );
        Path worktree = clone.resolveSibling("second");
        git(clone, "worktree", "add", "--quiet", "--detach", worktree.toString());

        List<String> command = ScanSecretsMojo.dockerCommand(HistoryMount.of(worktree), IMAGE);

        assertTrue(command.contains(worktree + ":" + worktree + ":ro"), "the worktree at the path it is known by");
        assertTrue(
            command.contains(clone.resolve(".git") + ":" + clone.resolve(".git") + ":ro"),
            "the clone's history at the path the worktree's pointer names"
        );
        assertEquals(worktree.toString(), command.get(command.indexOf(IMAGE) + 2), "the worktree is scanned");
        assertEquals(worktree + "/.gitleaks.toml", command.get(command.indexOf("--config") + 1));
        assertTrue(
            IntStream.range(0, command.size()).filter(index -> "-v".equals(command.get(index)))
                .allMatch(index -> command.get(index + 1).endsWith(":ro")),
            "the scanner must not change the clone it reads through the worktree"
        );
    }

    @Test
    @SneakyThrows
    void refusesTheIgnoreFileGitleaksWouldReadFromTheTree(@TempDir Path directory) {
        ScanSecretsMojo.refuseIgnoreFile(directory);
        Files.writeString(directory.resolve(".gitleaksignore"), "0123abcd:config.properties:generic-api-key:3\n");
        IOException refusal = assertThrows(IOException.class, () -> ScanSecretsMojo.refuseIgnoreFile(directory));
        assertEquals(
            "Remove " + directory.resolve(".gitleaksignore") + "; gitleaks drops every finding it lists, so an "
                + "exception belongs in .gitleaks.toml instead",
            refusal.getMessage()
        );
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
}
