package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A SNAPSHOT harness writes an application's first lockfile against the harness packages its starter declares,
 * each served from the tarball installed in the local Maven repository by a registry that is up before the
 * install starts.
 */
class LocalHarnessRegistryTest {

    private static final Path REPOSITORY = Path.of("repository");
    private static final String SNAPSHOT = "9.8.8-SNAPSHOT";
    private static final String SCRIPT = ".airness/npm-registry.py";
    // Stands in for the registry: it writes the port file it is given, then serves until it is stopped.
    private static final String SERVING = """
        while [ $# -gt 0 ]; do
            if [ "$1" = --port-file ]; then shift; printf 4873 > "$1"; fi
            shift
        done
        exec sleep 30
        """;

    @TempDir
    private Path directory;

    @Test
    void servesTheTarballOfEachHarnessPackageTheApplicationInstalls() {
        assertEquals(
            List.of(REPOSITORY.resolve("eu/ciechanowiec/airness-node/9.8.8-SNAPSHOT/airness-node-9.8.8-SNAPSHOT.tgz")),
            LocalHarnessRegistry.tarballs(REPOSITORY, Set.of("react", "@airness/node", "@airness/nodes"), SNAPSHOT),
            "a package no Airness module packs is installed from its own registry"
        );
    }

    @Test
    void servesNothingForAnApplicationWithoutAHarnessPackage() {
        assertEquals(List.of(), LocalHarnessRegistry.tarballs(REPOSITORY, Set.of("react"), SNAPSHOT));
    }

    @Test
    void refusesATarballThatIsNotInstalled() {
        Path missing = this.directory.resolve("airness-node.tgz");
        IOException refusal = assertThrows(IOException.class, () -> this.served(missing));
        assertTrue(String.valueOf(refusal.getMessage()).contains("Install the local Airness artifacts first"));
    }

    @Test
    @SneakyThrows
    void returnsOnceTheRegistryServesAndStopsItWhenClosed() {
        assertEquals("4873", this.served(this.application(SERVING)));
    }

    @Test
    void namesItsLogWhenTheRegistryStopsBeforeItServes() {
        Path tarball = this.application("exit 3\n");
        IOException refusal = assertThrows(IOException.class, () -> this.served(tarball));
        assertTrue(String.valueOf(refusal.getMessage()).contains("registry.log"));
    }

    // Starts the registry over one tarball, reads the port it serves on, and stops it.
    @SneakyThrows
    private String served(Path tarball) {
        Path logs = this.directory.resolve("logs");
        try (LocalHarnessRegistry _ = LocalHarnessRegistry.start("sh", this.directory, List.of(tarball), logs)) {
            return Files.readString(logs.resolve("registry.port"));
        }
    }

    @SneakyThrows
    private Path application(String script) {
        Path registry = this.directory.resolve(SCRIPT);
        Files.createDirectories(registry.getParent());
        Files.writeString(registry, script);
        return Files.writeString(this.directory.resolve("airness-node.tgz"), "tarball");
    }
}
