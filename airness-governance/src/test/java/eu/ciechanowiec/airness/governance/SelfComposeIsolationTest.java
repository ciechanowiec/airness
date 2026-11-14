package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * The compose projects Airness ships in its starters start beside another checkout of themselves.
 *
 * <p>A starter stores its compose file under an added {@code .fixture} suffix, which the goal never reads, so the
 * application a starter makes is held to the rule here, before anyone makes it.
 */
class SelfComposeIsolationTest {

    private static final String FIXTURE = "compose.yaml.fixture";

    @Test
    void shipsStartersWhoseComposeProjectsFixNoPortAndNoName() {
        List<Path> fixtures = starterFixtures();
        assertFalse(fixtures.isEmpty(), "a check that read no starter proves nothing about the starters");
        assertEquals(
            List.of(),
            fixtures.stream()
                .flatMap(fixture -> ComposeIsolationCheck.findings(name(fixture), read(fixture)).stream())
                .filter(verdict -> !verdict.clean())
                .map(Findings::report)
                .toList()
        );
    }

    private static String name(Path fixture) {
        return SelfModules.repository().relativize(fixture).toString();
    }

    @SneakyThrows
    private static List<Path> starterFixtures() {
        try (Stream<Path> starters = Files.list(SelfModules.repository().resolve("airness-starters"))) {
            return starters.map(starter -> starter.resolve(FIXTURE)).filter(Files::isRegularFile).sorted().toList();
        }
    }

    @SneakyThrows
    private static String read(Path file) {
        return Files.readString(file);
    }
}
