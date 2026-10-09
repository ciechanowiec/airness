package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginScanFilesTest {

    @TempDir
    private Path directory;

    @Test
    @SneakyThrows
    void preservesCollidingNamesAndClearsStaleInputs() {
        Path first = Files.createDirectory(this.directory.resolve("first"));
        Path second = Files.createDirectory(this.directory.resolve("second"));
        Path left = Files.writeString(first.resolve("library.jar"), "first archive");
        Path right = Files.writeString(second.resolve("library.jar"), "second archive");
        Path output = this.directory.resolve("scan");
        PluginScanFiles files = new PluginScanFiles(output);
        files.write(Map.of("first:library:1", left, "second:library:1", right));
        List<Path> originals = jars(output);
        assertEquals(2, originals.size());
        assertEquals(
            List.of("first archive", "second archive"), originals.stream().map(PluginScanFilesTest::read)
                .sorted().toList()
        );
        files.write(Map.of("first:library:1", left));
        assertEquals(1, jars(output).size());
        assertTrue(originals.contains(jars(output).getFirst()));
        files.write(Map.of());
        assertTrue(jars(output).isEmpty());
    }

    @Test
    @SneakyThrows
    void preservesCoordinatesForArchivesWithoutEmbeddedMetadata() {
        Path archive = Files.writeString(this.directory.resolve("library-2.1-native.jar"), "archive contents");
        Path output = this.directory.resolve("scan");
        new PluginScanFiles(output).write(Map.of("example:library:jar:native:2.1", archive));
        Path staged = jars(output).getFirst();
        String metadata = Files.readString(staged.resolveSibling("library-2.1-native.pom"));
        assertTrue(metadata.contains("<groupId>example</groupId>"));
        assertTrue(metadata.contains("<artifactId>library</artifactId>"));
        assertTrue(metadata.contains("<version>2.1</version>"));
        assertEquals(Files.readString(archive), Files.readString(staged));
    }

    @Test
    @SneakyThrows
    void preservesPomArtifactsWithoutReplacingTheirContents() {
        Path original = Files.writeString(this.directory.resolve("library.pom"), "<project/>");
        Path output = this.directory.resolve("scan");
        new PluginScanFiles(output).write(Map.of("example:library:pom:2.1", original));
        try (Stream<Path> files = Files.walk(output)) {
            List<Path> copies = files.filter(Files::isRegularFile).toList();
            assertEquals(1, copies.size());
            assertEquals(Files.readString(original), Files.readString(copies.getFirst()));
        }
    }

    @Test
    void rejectsUnresolvedArchivesAndDirectoryInputs() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new PluginScanFiles(this.directory.resolve("scan"))
                .write(Map.of("missing", this.directory.resolve("missing.jar")))
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new PluginScanFiles(this.directory.resolve("scan/child")).write(
                Map.of("directory", this.directory)
            )
        );
    }

    @Test
    @SneakyThrows
    void reportsAnUnwritableOutputDirectory() {
        Path occupied = Files.writeString(this.directory.resolve("occupied"), "not a directory");
        assertThrows(
            UncheckedIOException.class,
            () -> new PluginScanFiles(occupied.resolve("scan")).write(Map.of())
        );
    }

    @SneakyThrows
    private static List<Path> jars(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".jar")).sorted()
                .toList();
        }
    }

    @SneakyThrows
    private static String read(Path path) {
        return Files.readString(path);
    }
}
