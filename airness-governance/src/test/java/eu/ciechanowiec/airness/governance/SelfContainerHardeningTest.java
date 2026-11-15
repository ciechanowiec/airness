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
 * The containers Airness ships in its starters keep their isolation and write no credential.
 *
 * <p>A starter stores its compose file under an added {@code .fixture} suffix, which the goal never reads, so the
 * application a starter makes is held to the rule here, before anyone makes it.
 */
class SelfContainerHardeningTest {

    @Test
    void shipsStartersWhoseContainersKeepTheirIsolation() {
        List<Path> composeFiles = starterFiles("compose.yaml.fixture");
        List<Path> dockerfiles = starterFiles("Dockerfile");
        assertFalse(composeFiles.isEmpty(), "a check that read no starter proves nothing about the starters");
        assertFalse(dockerfiles.isEmpty(), "a check that read no starter proves nothing about the starters");
        Stream<Findings> verdicts = Stream.concat(
            composeFiles.stream().flatMap(
                file -> ContainerHardeningCheck.composeFindings(name(file), read(file)).stream()
            ),
            dockerfiles.stream().map(file -> ContainerHardeningCheck.dockerfileFindings(name(file), read(file)))
        );
        assertEquals(List.of(), verdicts.filter(verdict -> !verdict.clean()).map(Findings::report).toList());
    }

    private static String name(Path file) {
        return SelfModules.repository().relativize(file).toString();
    }

    @SneakyThrows
    private static List<Path> starterFiles(String fileName) {
        try (Stream<Path> starters = Files.list(SelfModules.repository().resolve("airness-starters"))) {
            return starters.map(starter -> starter.resolve(fileName)).filter(Files::isRegularFile).sorted().toList();
        }
    }

    @SneakyThrows
    private static String read(Path file) {
        return Files.readString(file);
    }
}
