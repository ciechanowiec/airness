package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every test of the build reads the managed Git configuration, whatever the machine running it configured.
 *
 * <p>The parents point {@code GIT_CONFIG_GLOBAL} at {@code .airness/test.gitconfig} and set
 * {@code GIT_CONFIG_NOSYSTEM}. Each case below would fail on a machine whose own configuration reached the
 * test: a fixture repository whose own hooks all fail still commits, branches and merges, and an ignore or
 * attribute file the machine keeps for its user is not read.
 */
class TestGitConfigurationTest {

    private static final String GLOBAL = "GIT_CONFIG_GLOBAL";
    private static final String FAILING_HOOK = "#!/bin/sh\nexit 1\n";
    private static final List<String> HOOKS = List.of(
        "pre-commit", "commit-msg", "post-checkout", "pre-merge-commit", "post-merge"
    );
    private static final String SECRET = "secret.txt";
    // A merge commit names itself and its two parents.
    private static final int MERGE_FIELDS = 3;

    @TempDir
    private Path machine;

    @Test
    @SneakyThrows
    void pointsEveryTestAtTheManagedConfigurationAndAtNoSystemOne() {
        Path managed = Path.of("..", ".airness", "test.gitconfig").toRealPath();
        Path named = Path.of(Optional.ofNullable(System.getenv(GLOBAL)).orElse("")).toRealPath();
        assertAll(
            () -> assertEquals(managed, named),
            () -> assertEquals(Optional.of("1"), Optional.ofNullable(System.getenv("GIT_CONFIG_NOSYSTEM")))
        );
    }

    @Test
    @SneakyThrows
    void commitsBranchesAndMergesPastFailingHooksTheRepositoryHolds() {
        GitFixture fixture = new GitFixture("hooked");
        for (String hook : HOOKS) {
            Path file = Files.writeString(fixture.root().resolve(".git/hooks").resolve(hook), FAILING_HOOK);
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
        fixture.write("base.txt", "Base.\n").stage();
        fixture.git("commit", "--quiet", "--message", "feat(core): add the base fixture file");
        fixture.mergeASideBranch();
        String merge = answer(fixture.root(), Map.of(), "rev-list", "--parents", "--max-count=1", "HEAD");
        assertEquals(MERGE_FIELDS, merge.split(" ").length, merge);
    }

    @Test
    void readsNeitherTheIgnoreFileNorTheAttributeFileTheMachineKeepsForItsUser() {
        this.write("git/ignore", SECRET + '\n');
        this.write("git/attributes", "*.txt diff=hostile\n");
        GitFixture fixture = new GitFixture("machine-files").write(SECRET, "Secret.\n");
        Map<String, String> environment = Map.of("XDG_CONFIG_HOME", this.machine.toString());
        assertAll(
            () -> assertEquals(
                "?? " + SECRET, answer(fixture.root(), environment, "status", "--porcelain", "--untracked-files=all")
            ),
            () -> assertEquals(
                SECRET + ": diff: unspecified", answer(fixture.root(), environment, "check-attr", "diff", "--", SECRET)
            )
        );
    }

    @Test
    void startsANewRepositoryOnMainAndTurnsSigningOff() {
        GitFixture fixture = new GitFixture("defaults");
        assertAll(
            () -> assertEquals("main", answer(fixture.root(), Map.of(), "symbolic-ref", "--short", "HEAD")),
            () -> assertEquals("false", answer(fixture.root(), Map.of(), "config", "--global", "tag.gpgSign"))
        );
    }

    // Writes one file of the configuration directory the machine keeps for its user.
    @SneakyThrows
    private void write(String relative, String content) {
        Path file = this.machine.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    // What git prints for one command in the fixture, under this test's environment and the variables given.
    private static String answer(Path root, Map<String, String> environment, String... arguments) {
        ProcessBuilder builder = new ProcessBuilder(
            Stream.concat(Stream.of("git", "-C", root.toString()), Stream.of(arguments)).toList()
        ).redirectErrorStream(true);
        builder.environment().putAll(environment);
        try {
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            assertEquals(0, process.waitFor(), output);
            return output;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running git", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not run git", exception);
        }
    }
}
