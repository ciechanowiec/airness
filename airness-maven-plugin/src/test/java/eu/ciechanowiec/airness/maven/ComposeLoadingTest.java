package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that every non-zero exit of a compose load is a finding naming the file, using real processes
 * that stand where compose would.
 */
class ComposeLoadingTest {

    private static final Path COMPOSE = Path.of("deploy", "compose.yaml");

    @SneakyThrows
    private static List<String> load(Path directory, String script) {
        Files.createDirectories(directory.resolve("deploy"));
        return new ComposeLoading(List.of("sh", "-c", script, "compose"))
            .load(directory, COMPOSE, directory.resolve("report.txt"));
    }

    @Test
    void passesAProjectThatLoads(@TempDir Path directory) {
        assertEquals(List.of(), load(directory, "exit 0"), "a zero exit is a loaded project");
    }

    @Test
    void namesEachLineOfTheReasonALoadFailed(@TempDir Path directory) {
        assertEquals(
            List.of(COMPOSE + ": services must be a mapping"),
            load(directory, "printf 'services must be a mapping\\n\\n' >&2; exit 15"),
            "each reason line is named with the file, and blank lines are not"
        );
    }

    @Test
    void namesTheExitWhenTheFailureGivesNoReason(@TempDir Path directory) {
        assertEquals(
            List.of(COMPOSE + ": compose config exited 3 without a reason"), load(directory, "exit 3"),
            "a silent failure is still a failure"
        );
    }

    @Test
    void choosesTheFirstAvailableCommand(@TempDir Path directory) {
        List<List<String>> candidates = List.of(
            List.of("airness-no-such-compose"), List.of("sh", "-c", "exit 1"), List.of("true")
        );
        assertEquals(
            new ComposeLoading(List.of("true")), chosen(candidates, directory),
            "a missing binary and a failing probe are both unavailable"
        );
        assertThrows(
            IOException.class, () -> ComposeLoading.choose(List.of(List.of("false")), directory, directory),
            "no compose at all is an operational failure"
        );
    }

    @SneakyThrows
    private static ComposeLoading chosen(List<List<String>> candidates, Path directory) {
        return ComposeLoading.choose(candidates, directory, directory);
    }
}
