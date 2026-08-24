package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import eu.ciechanowiec.airness.governance.ArtifactContentCheck;
import eu.ciechanowiec.airness.governance.Findings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * Airness reads its own finished artifacts with the rule it ships for a consumer's.
 *
 * <p>The {@code artifact-content} goal runs at package time on the module that produced the archive,
 * and no module of this build can run a goal of the plugin this build produces. What is reachable is
 * the moment this module's tests run: every module before it in the reactor has already been packaged
 * and installed, so its archive is on disk and can be read the way the goal would read it.
 *
 * <p>The set of archives is asserted rather than merely iterated. A test that reads whatever it happens
 * to find reports a clean verdict when it finds nothing, and a renamed module or a changed reactor order
 * would turn this into a test that passes by reading no archive at all.
 *
 * <p>One archive stays out of reach and is named here rather than left to be discovered: this module's
 * own. It does not exist while its own tests run, and no earlier module can read it. Closing that needs
 * a test source on {@code airness-parent}, which is the last module and the only place where all six
 * archives exist at once.
 */
class SelfArtifactContentTest {

    private static final List<String> PACKAGED_BEFORE_THIS_MODULE = List.of(
        "airness-config", "airness-assets", "airness-annotations", "airness-governance"
    );

    @Test
    void packagesNothingIntoItsOwnArchivesThatItRefusesInAConsumersOne() {
        Path repository = SelfModules.repository();
        List<Path> modules = PACKAGED_BEFORE_THIS_MODULE.stream().map(repository::resolve).toList();
        assertEquals(
            List.of(),
            modules.stream()
                .filter(module -> !Files.isDirectory(module.resolve("target")))
                .map(Path::toString)
                .toList(),
            "every module before this one in the reactor is packaged by the time these tests run"
        );
        assertEquals(
            List.of(),
            modules.stream()
                .flatMap(module -> broken(check(repository, module).findings()).stream())
                .toList(),
            "an archive of this repository answers to the rule this repository publishes for consumers"
        );
    }

    private static ArtifactContentCheck check(Path repository, Path module) {
        return new ArtifactContentCheck(
            archive(module),
            module.resolve("target/classes"),
            module.resolve("target/test-classes"),
            repository
        );
    }

    @SneakyThrows
    private static Path archive(Path module) {
        String name = module.getFileName().toString();
        try (Stream<Path> built = Files.list(module.resolve("target"))) {
            List<Path> archives = built
                .filter(candidate -> candidate.getFileName().toString().startsWith(name + "-"))
                .filter(candidate -> candidate.getFileName().toString().endsWith(".jar"))
                .toList();
            if (archives.size() != 1) {
                throw new IllegalStateException(
                    "expected one packaged archive under " + module + " but found " + archives
                );
            }
            return archives.getFirst();
        }
    }

    private static List<String> broken(Collection<Findings> findings) {
        return findings.stream().filter(verdict -> !verdict.clean()).map(Findings::report).toList();
    }
}
