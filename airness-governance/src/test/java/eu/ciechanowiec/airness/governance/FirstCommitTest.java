package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FirstCommitTest {

    private static final Year FIRST = Year.of(2019);

    @Test
    void readsTheYearOfTheFirstCommit() {
        GitFixture fixture = new GitFixture("first-commit-year");
        commit(fixture.write("a.txt", "A.\n"), "2019-05-01T12:00:00");
        commit(fixture.write("b.txt", "B.\n"), "2024-05-01T12:00:00");
        assertEquals(Optional.of(FIRST), FirstCommit.year(fixture.root()));
    }

    @Test
    void answersNothingForAnUnbornHistory() {
        assertEquals(Optional.empty(), FirstCommit.year(new GitFixture("first-commit-unborn").root()));
    }

    @Test
    void answersNothingForAShallowClone(@TempDir Path directory) {
        GitFixture fixture = new GitFixture("first-commit-origin");
        commit(fixture.write("a.txt", "A.\n"), "2019-05-01T12:00:00");
        commit(fixture.write("b.txt", "B.\n"), "2024-05-01T12:00:00");
        Path clone = directory.resolve("clone");
        run(List.of("git", "clone", "--quiet", "--depth", "1", fixture.root().toUri().toString(), clone.toString()));
        assertEquals(Optional.empty(), FirstCommit.year(clone));
    }

    private static void commit(GitFixture fixture, String date) {
        fixture.stage();
        fixture.git(
            "commit", "--quiet", "--no-verify", "--date=" + date, "--message", "feat(core): add a fixture file"
        );
    }

    @SneakyThrows
    private static void run(List<String> command) {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        assertEquals(0, process.waitFor(), String.join(" ", command));
    }
}
