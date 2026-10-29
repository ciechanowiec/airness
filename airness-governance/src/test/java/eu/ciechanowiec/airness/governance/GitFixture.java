package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.SneakyThrows;

/**
 * A real git working tree in the system temporary directory, built one file at a time.
 *
 * <p>The checks read a repository through {@code git} itself, so a fixture that stood in for one would
 * be testing the stand-in. What a check does with an ignored file, an untracked file, or a path that is
 * not there is exactly the behaviour worth pinning, and every one of those answers comes from git rather
 * than from anything this project wrote. So the fixture is a repository, and the only thing it stands in
 * for is the content.
 *
 * <p>Each fixture gets a unique directory, so that no test can observe or disturb the repository another
 * one is using. Every command pins both the Git directory and working tree, so even a damaged fixture
 * cannot discover and modify the repository containing the tests. Hooks and signing are turned off,
 * because a fixture commit must not depend on how the machine running it is configured.
 *
 * <p>Every fixture of one test JVM sits beneath a single directory of the system temporary directory,
 * outside the project, so that no nested repository is left in the build output. The directory is created
 * with the first fixture and created again when a fixture follows its removal. A fixture's location is the
 * real path, because git answers with the resolved top level, and on macOS the temporary directory is
 * reached through a symbolic link. {@link GitFixtureCleanup} removes the directory whenever a launcher
 * execution ends, unless a failure has been recorded in it. A recorded failure stays for the rest of the
 * JVM, so the repositories of a failure are kept, named, and never removed by a later passing execution.
 */
record GitFixture(Path root) {

    private static final Path SCRATCH = Path.of(
        System.getProperty("java.io.tmpdir"), "airness-git-fixtures-" + UUID.randomUUID()
    );
    private static final Path FAILED = SCRATCH.resolve(".failed");

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
     * Writes one binary file into the fixture.
     *
     * @return this fixture, so writes chain
     */
    @SneakyThrows
    GitFixture write(String relative, byte[] content) {
        Path file = this.root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, content);
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

    /**
     * Records that a test of this JVM failed, so that its fixtures outlive every later execution.
     */
    @SneakyThrows
    static void recordFailure() {
        Files.createDirectories(SCRATCH);
        Files.writeString(FAILED, "A test of this run failed.\n");
    }

    /**
     * Ends an execution's fixtures: deletes them unless a failure was recorded, and names where they are then.
     */
    static void conclude() {
        if (Files.exists(FAILED)) {
            System.getLogger(GitFixture.class.getName())
                .log(System.Logger.Level.WARNING, "Git fixtures kept for inspection: {0}", SCRATCH);
        } else if (Files.isDirectory(SCRATCH)) {
            delete();
        }
    }

    @SneakyThrows
    private static void delete() {
        try (Stream<Path> paths = Files.walk(SCRATCH)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    @SneakyThrows
    private static Path location(String name) {
        Files.createDirectories(SCRATCH);
        return Files.createTempDirectory(SCRATCH, name + '-').toRealPath();
    }
}
