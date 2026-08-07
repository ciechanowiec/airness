package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.revelc.code.formatter.java.JavaFormatter;
import net.revelc.code.impsort.ImpSort;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Test;

/**
 * Airness formats its own sources with the formatter it ships, and orders their imports the same way.
 *
 * <p>The goal that enforces this on a consumer cannot reach this repository. A Maven plugin is resolved
 * from a repository rather than from the reactor that builds it, so no module of this build can run a
 * goal of the plugin this build produces. The engines behind the goal are reachable, though, because
 * {@link SourceFormattingMojo} keeps them behind static calls that take a path.
 *
 * <p>Without this, the one project that publishes the formatter was the one project not held to it, and
 * the drift that follows is invisible rather than gradual: nothing reports it until a consumer asks why
 * the harness does not look like what it enforces.
 */
class SelfFormattingTest {

    private static final List<String> MODULES = List.of(
        "airness-annotations", "airness-governance", "airness-maven-plugin"
    );
    private static final List<String> SOURCES = List.of("src/main/java", "src/test/java");

    @Test
    void formatsItsOwnSourcesWithTheFormatterItShips() {
        JavaFormatter formatter = SourceFormattingMojo.formatter(new SystemStreamLog(), target());
        List<Path> sources = SourceFormattingMojo.javaSources(roots());
        assertTrue(sources.size() > 1, "a check that read nothing proves nothing about this repository");
        assertEquals(
            List.of(),
            sources.stream()
                .filter(source -> !SourceFormattingMojo.formatted(formatter, source))
                .map(SelfFormattingTest::relative)
                .toList(),
            "the project that publishes this formatter is the first that has to match it"
        );
    }

    @Test
    void normalizesItsOwnImportsTheWayItShips() {
        ImpSort sorter = SourceFormattingMojo.sorter();
        List<Path> sources = SourceFormattingMojo.javaSources(roots());
        assertTrue(sources.size() > 1, "a check that read nothing proves nothing about this repository");
        assertEquals(
            List.of(),
            sources.stream()
                .filter(source -> !SourceFormattingMojo.sorted(sorter, source))
                .map(SelfFormattingTest::relative)
                .toList(),
            "import order is half of what the source-formatting goal asserts, so it is half of this"
        );
    }

    private static String relative(Path source) {
        return repository().relativize(source).toString();
    }

    private static String target() {
        return Path.of("target").toAbsolutePath().toString();
    }

    private static List<Path> roots() {
        Path repository = repository();
        return MODULES.stream()
            .flatMap(module -> SOURCES.stream().map(source -> repository.resolve(module).resolve(source)))
            .filter(Files::isDirectory)
            .toList();
    }

    private static Path repository() {
        Path current = Path.of("").toAbsolutePath();
        return Stream.of(current, current.getParent())
            .filter(path -> Files.exists(path.resolve("airness-parent/pom.xml")))
            .findFirst()
            .orElseThrow();
    }
}
