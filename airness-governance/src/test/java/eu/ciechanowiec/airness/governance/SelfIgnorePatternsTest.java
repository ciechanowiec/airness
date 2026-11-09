package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * Airness repeats no ignore pattern: not in its own ignore files, not across the managed blocks it ships, and not
 * in the ignore files its starter projects keep below those blocks.
 *
 * <p>The goal runs over this repository in the build of {@code airness-parent}, which a build narrowed with
 * {@code -pl} never reaches, and it never names a managed line, because a project cannot remove one. Every managed
 * block of one kind of ignore file may meet every other in one file, at a root that carries every layer, so the
 * shipped blocks are held here to declaring each pattern once among all of them.
 */
class SelfIgnorePatternsTest {

    private static final String GIT_IGNORE = ".gitignore";
    private static final String DOCKER_IGNORE = ".dockerignore";
    private static final String ASSET = ".asset";
    private static final String FIXTURE = ".fixture";
    private static final List<String> SHIPPED = List.of(
        "src/main/resources/airness/files", "src/main/resources/airness/project-files"
    );

    @Test
    void repeatsNoPatternInItsOwnIgnoreFiles() {
        IgnorePatternCheck check = new IgnorePatternCheck(SelfModules.repository());
        assertAll(
            () -> assertTrue(
                check.scanned() > 0, "a check that read no ignore file proves nothing about this repository"
            ),
            () -> assertEquals(
                List.of(),
                check.findings().stream().filter(verdict -> !verdict.clean()).map(Findings::report).toList()
            )
        );
    }

    @Test
    void shipsEachManagedPatternOnceAcrossTheBlocksOfOneKindOfIgnoreFile() {
        List<IgnorePattern> gitIgnore = IgnoreFile.parse(GIT_IGNORE, shipped(GIT_IGNORE)).patterns();
        assertAll(
            () -> assertTrue(gitIgnore.size() > 1, "a check that read no block proves nothing about the shipped ones"),
            () -> assertEquals(Map.of(), repeated(gitIgnore)),
            () -> assertEquals(Map.of(), repeated(IgnoreFile.parse(DOCKER_IGNORE, shipped(DOCKER_IGNORE)).patterns()))
        );
    }

    @Test
    void repeatsNoManagedPatternInTheIgnoreFilesOfAStarterProject() {
        List<Path> fixtures = starterFixtures();
        String blocks = shipped(GIT_IGNORE);
        assertAll(
            () -> assertFalse(fixtures.isEmpty(), "a check that read no starter proves nothing about the starters"),
            () -> assertEquals(
                List.of(),
                fixtures.stream()
                    .map(fixture -> IgnoreFile.parse(GIT_IGNORE, blocks + "\n" + read(fixture)))
                    .flatMap(file -> IgnorePatternCheck.repeats(List.of(file), List.of()).stream())
                    .filter(repeat -> !repeat.repeat().managed())
                    .map(IgnoreRepeat::offence)
                    .toList()
            )
        );
    }

    // Every shipped block of one kind of ignore file, each between the markers of the layer that ships it, in the
    // modules that ship managed files.
    @SneakyThrows
    private static String shipped(String name) {
        try (Stream<Path> modules = Files.list(SelfModules.repository())) {
            return modules.flatMap(module -> SHIPPED.stream().map(module::resolve))
                .filter(Files::isDirectory)
                .flatMap(SelfIgnorePatternsTest::layers)
                .filter(layer -> Files.isRegularFile(layer.resolve(name + ASSET)))
                .sorted()
                .map(layer -> block(layer, layer.resolve(name + ASSET)))
                .collect(Collectors.joining("\n"));
        }
    }

    @SneakyThrows
    private static Stream<Path> layers(Path shipped) {
        try (Stream<Path> layers = Files.list(shipped)) {
            return layers.toList().stream();
        }
    }

    private static String block(Path layer, Path asset) {
        String owner = String.valueOf(layer.getFileName());
        return "# BEGIN AIRNESS MANAGED: " + owner + "\n" + read(asset) + "# END AIRNESS MANAGED: " + owner + "\n";
    }

    private static Map<String, Long> repeated(List<IgnorePattern> patterns) {
        return patterns.stream()
            .collect(Collectors.groupingBy(IgnorePattern::text, Collectors.counting()))
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue() > 1)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @SneakyThrows
    private static List<Path> starterFixtures() {
        try (Stream<Path> starters = Files.list(SelfModules.repository().resolve("airness-starters"))) {
            return starters.map(starter -> starter.resolve(GIT_IGNORE + FIXTURE)).filter(Files::isRegularFile)
                .toList();
        }
    }

    @SneakyThrows
    private static String read(Path file) {
        return Files.readString(file);
    }
}
