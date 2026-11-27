package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Airness lays out its own poms, and writes them as portably, as it requires of every consumer.
 *
 * <p>The goal that holds a consumer's pom to the layout and to portable declarations runs in no module of this build,
 * and leaves the harness parents alone in every build. This reads every tracked pom of the repository instead, the
 * starters and the
 * integration fixtures stored under a {@code .fixture} suffix among them, so no pom of the harness passes only
 * because the goal never reached it.
 */
class SelfPomLayoutTest {

    @Test
    void writesEveryTrackedPomAsTheLayoutAndPortabilityChecksRequire() {
        Path root = SelfModules.repository();
        List<Path> poms = Repository.trackedFiles(root).stream()
            .filter(file -> List.of("pom.xml", "pom.xml.fixture").contains(file.getFileName().toString()))
            .toList();
        assertAll(
            () -> assertFalse(poms.isEmpty(), "a check that read nothing proves nothing about this repository"),
            () -> assertEquals(List.of(), poms.stream().flatMap(pom -> broken(root, pom)).toList())
        );
    }

    private static Stream<String> broken(Path root, Path pom) {
        Stream<Findings> verdicts = Stream.concat(
            new PomLayoutCheck(pom).findings().stream(), new PomPortabilityCheck(pom, root).findings().stream()
        );
        return verdicts.filter(verdict -> !verdict.clean())
            .map(verdict -> root.relativize(pom) + ": " + verdict.report());
    }
}
