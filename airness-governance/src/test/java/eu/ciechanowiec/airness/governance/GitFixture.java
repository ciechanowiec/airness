package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;

/**
 * A real git working tree under {@code target/}, built one file at a time.
 *
 * <p>The checks read a repository through {@code git} itself, so a fixture that stood in for one would
 * be testing the stand-in. What a check does with an ignored file, an untracked file, or a path that is
 * not there is exactly the behaviour worth pinning, and every one of those answers comes from git rather
 * than from anything this project wrote. So the fixture is a repository, and the only thing it stands in
 * for is the content.
 *
 * <p>Each fixture gets a unique directory, because mutation workers can execute the same test in
 * parallel. Every command pins both the Git directory and working tree, so even a damaged fixture
 * cannot discover and modify the repository containing the tests. Hooks and signing are turned off,
 * because a fixture commit must not depend on how the machine running it is configured.
 */
record GitFixture(Path root) {

    private static final Path SCRATCH = Path.of("target", "fixtures");

    @SneakyThrows
    GitFixture(String name) {
        this(location(name));
        run(List.of("git", "init", "--quiet", this.root.toString()));
        this.git("config", "user.name", "Fixture");
        this.git("config", "user.email", "fixture@example.invalid");
        this.git("config", "commit.gpgsign", "false");
    }

    /**
     * Writes one file into the fixture.
     *
     * @return this fixture, so writes chain
     */
    @SneakyThrows
    GitFixture write(String relative, CharSequence content) {
        Path file = this.root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return this;
    }

    /**
     * Stages everything present, without recording it.
     *
     * <p>A pending commit message is checked against what is staged, so the fixture has to be able to
     * stop here. Committing as well would leave nothing in the index to size the message against.
     *
     * @return this fixture, so calls chain
     */
    GitFixture stage() {
        this.git("add", "--all");
        return this;
    }

    /**
     * Stages everything present and records it under the given message.
     *
     * @return this fixture, so commits chain
     */
    GitFixture commit(String message) {
        this.stage();
        this.git("commit", "--quiet", "--no-verify", "--message", message);
        return this;
    }

    /**
     * Records a merge commit: a side branch, a commit on each side, and a merge back under the header
     * git writes itself.
     *
     * <p>Two checks need this topology and neither can be shown it any other way, because a merge is
     * what git recorded as a second parent and not anything a message claims.
     *
     * @return this fixture, so calls chain
     */
    GitFixture mergeASideBranch() {
        this.git("checkout", "-b", "side");
        this.write("side.txt", "Side.\n").commit("feat(core): add the side fixture file");
        this.git("checkout", "-");
        this.write("main.txt", "Main.\n").commit("feat(core): add the main fixture file");
        this.git("merge", "--no-ff", "side", "--message", "Merge branch 'side'");
        return this;
    }

    void git(String... arguments) {
        List<String> command = Stream.concat(
            Stream.of(
                "git",
                "--git-dir=" + this.root.resolve(".git"),
                "--work-tree=" + this.root
            ),
            Stream.of(arguments)
        ).toList();
        run(command);
    }

    private static void run(List<String> command) {
        ProcessBuilder builder = new ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD);
        try {
            int code = builder.start().waitFor();
            if (code != 0) {
                throw new IllegalStateException("Fixture git command exited with code " + code);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running fixture git", exception);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not run fixture git", exception);
        }
    }

    @SneakyThrows
    private static Path location(String name) {
        Files.createDirectories(SCRATCH);
        return Files.createTempDirectory(SCRATCH, name + '-').toAbsolutePath().normalize();
    }
}
