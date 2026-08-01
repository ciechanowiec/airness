package eu.ciechanowiec.airness.governance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
 * <p>Each fixture is deleted and rebuilt on construction, so a run reads what this test wrote rather
 * than what the last one left. Hooks and signing are turned off, because a fixture commit must not
 * depend on how the machine running it is configured.
 */
final class GitFixture {

    private static final Path SCRATCH = Path.of("target", "fixtures");

    private final Path root;

    @SneakyThrows
    GitFixture(String name) {
        this.root = SCRATCH.resolve(name).toAbsolutePath().normalize();
        delete(this.root);
        Files.createDirectories(this.root);
        this.git("init", "--quiet");
        this.git("config", "user.name", "Fixture");
        this.git("config", "user.email", "fixture@example.invalid");
        this.git("config", "commit.gpgsign", "false");
    }

    /**
     * @return the working tree root, absolute and normalized
     */
    Path root() {
        return this.root;
    }

    /**
     * @return this fixture, so writes chain
     */
    @SneakyThrows
    GitFixture write(String relative, String content) {
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

    private void git(String... arguments) {
        GitPlumbing.run(this.root, List.of(arguments));
    }

    private static void delete(Path directory) {
        Optional.of(directory).filter(Files::exists).ifPresent(GitFixture::deleteTree);
    }

    @SneakyThrows
    private static void deleteTree(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(GitFixture::deleteOne);
        }
    }

    @SneakyThrows
    private static void deleteOne(Path path) {
        Files.delete(path);
    }
}
