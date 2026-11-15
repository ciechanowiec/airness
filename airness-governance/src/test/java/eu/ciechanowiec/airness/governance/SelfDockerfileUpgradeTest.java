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
 * The images Airness ships in its starters take their system packages from their base, so the application a
 * starter makes is held to the rule before anyone makes it.
 */
class SelfDockerfileUpgradeTest {

    @Test
    void shipsStartersWhoseDockerfilesUpgradeNothingTheirBaseHolds() {
        List<Path> dockerfiles = starterDockerfiles();
        assertFalse(dockerfiles.isEmpty(), "a check that read no starter proves nothing about the starters");
        assertEquals(
            List.of(),
            dockerfiles.stream().flatMap(file -> offences(file).stream()).toList()
        );
    }

    @SneakyThrows
    private static List<Path> starterDockerfiles() {
        try (Stream<Path> starters = Files.list(SelfModules.repository().resolve("airness-starters"))) {
            return starters.map(starter -> starter.resolve("Dockerfile")).filter(Files::isRegularFile).sorted()
                .toList();
        }
    }

    private static List<String> offences(Path file) {
        return DockerfileUpgradeCheck.offences(SelfModules.repository().relativize(file).toString(), read(file));
    }

    @SneakyThrows
    private static String read(Path file) {
        return Files.readString(file);
    }
}
