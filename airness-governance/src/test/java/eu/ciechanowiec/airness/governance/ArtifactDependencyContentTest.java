package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArtifactDependencyContentTest {

    private static final String DEVELOPMENT = "Source or development files packaged in the JAR";
    private static final String SECRETS = "Recognizable secret material packaged in the JAR";
    private static final String MAIN = "main";
    private static final String APPLICATION = "application.properties";
    private static final String VALUE = "value";
    private static final String BYTECODE = "bytecode";

    @TempDir
    private Path directory;

    @Test
    void rejectsAnUnreadableDependencyArchive() {
        Path vendor = this.archives().output("broken", "dependency.jar", "not an archive").resolve("dependency.jar");
        Path shipped = this.archives().jar(Map.of(APPLICATION, VALUE));
        assertThrows(UncheckedIOException.class, () -> this.findings(shipped, this.directory, vendor, SECRETS));
    }

    @Test
    void readsSecretsWhoseNameAlsoOccursInADependency() {
        Path vendor = this.archives().jar(Map.of(APPLICATION, VALUE));
        Path shipped = this.archives().jar(Map.of(APPLICATION, "ghp_12345678901234567890"));
        assertEquals(
            List.of(APPLICATION),
            this.findings(shipped, this.directory.resolve(MAIN), vendor, SECRETS)
        );
    }

    @Test
    void readsChangedDevelopmentFilesWhoseNameAlsoOccursInADependency() {
        Path vendor = this.archives().jar(Map.of("Source.java", VALUE));
        Path shipped = this.archives().jar(Map.of("Source.java", "module source"));
        assertEquals(
            List.of("Source.java"),
            this.findings(shipped, this.directory.resolve(MAIN), vendor, DEVELOPMENT)
        );
    }

    @Test
    void neverExemptsModuleOutputEvenWhenADependencyPublishesIdenticalBytes() {
        for (String prefix : List.of("", "BOOT-INF/classes/")) {
            String name = "Source.java";
            Path main = this.archives().output(MAIN, name, BYTECODE);
            Path vendor = this.archives().jar(Map.of(prefix + name, BYTECODE));
            Path shipped = this.archives().jar(Map.of(prefix + name, BYTECODE));
            assertEquals(
                List.of(prefix + name),
                this.findings(shipped, main, vendor, DEVELOPMENT)
            );
        }
    }

    @Test
    void readsModuleOwnedSecretsEvenWhenDependencyBytesAreIdentical() {
        String credential = "ghp_12345678901234567890";
        for (String prefix : List.of("", "BOOT-INF/classes/")) {
            Path main = this.archives().output(MAIN, APPLICATION, credential);
            Path vendor = this.archives().jar(Map.of(prefix + APPLICATION, credential));
            Path shipped = this.archives().jar(Map.of(prefix + APPLICATION, credential));
            assertEquals(
                List.of(prefix + APPLICATION),
                this.findings(shipped, main, vendor, SECRETS)
            );
        }
    }

    @Test
    void acceptsTheDirectoriesAPackagingPluginWroteAboveADependencysFile() {
        Path unlisted = this.archives().jar(Map.of(".idea/workspace.xml", VALUE));
        Path nested = this.archives().jar(Map.of(".idea/shelf/change.xml", VALUE));
        Path listed = this.archives().jar(Map.of(".idea/", "", ".idea/workspace.xml", VALUE));
        for (Path vendor : List.of(unlisted, nested, listed)) {
            Path shipped = this.archives().jar(vendored(vendor));
            assertEquals(List.of(), this.findings(shipped, this.directory.resolve(MAIN), vendor, DEVELOPMENT));
        }
    }

    @Test
    void readsADevelopmentDirectoryNoDependencyFileLiesBeneath() {
        Path vendor = this.archives().jar(Map.of(".idea-tools/settings.xml", VALUE, "lib/.idea", VALUE));
        Path shipped = this.archives().jar(Map.of(".idea/", ""));
        assertEquals(
            List.of(".idea/"),
            this.findings(shipped, this.directory.resolve(MAIN), vendor, DEVELOPMENT)
        );
    }

    @Test
    void readsAModuleFileInADirectoryADependencySupplies() {
        Path vendor = this.archives().jar(Map.of(".idea/workspace.xml", VALUE));
        Path shipped = this.archives().jar(Map.of(".idea/", "", ".idea/workspace.xml", VALUE, ".idea/own.xml", VALUE));
        assertEquals(
            List.of(".idea/own.xml"),
            this.findings(shipped, this.directory.resolve(MAIN), vendor, DEVELOPMENT)
        );
    }

    // What a packaging plugin builds from a dependency: its file entries, with a directory entry above each.
    private static Map<String, String> vendored(Path vendor) {
        return files(vendor).stream()
            .flatMap(name -> Stream.concat(parents(name), Stream.of(name)))
            .distinct()
            .collect(
                Collectors.toMap(
                    entry -> entry, entry -> entry.endsWith("/") ? "" : VALUE, (first, _) -> first,
                    LinkedHashMap::new
                )
            );
    }

    @SneakyThrows
    private static List<String> files(Path archive) {
        try (JarFile jar = new JarFile(archive.toFile())) {
            return jar.stream().filter(entry -> !entry.isDirectory()).map(JarEntry::getName).toList();
        }
    }

    private static Stream<String> parents(String name) {
        return IntStream.range(0, name.length())
            .filter(index -> name.charAt(index) == '/')
            .mapToObj(index -> name.substring(0, index + 1));
    }

    private ArchiveFixture archives() {
        return new ArchiveFixture(this.directory);
    }

    private List<String> findings(Path shipped, Path main, Path vendor, String headline) {
        return ArchiveFixture.offences(
            new ArtifactContentCheck(
                shipped, new ModuleOutput(main, this.directory.resolve("test")), this.directory, List.of(vendor)
            ).findings(), headline
        );
    }
}
