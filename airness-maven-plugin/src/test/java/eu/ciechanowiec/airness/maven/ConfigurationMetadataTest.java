package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.ConfigurationProperty;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationMetadataTest {

    private static final String ENTRY = "META-INF/spring-configuration-metadata.json";

    private static final String WITHDRAWN = """
        {
          "groups": [
            {"name": "server.error", "type": "org.example.ErrorProperties"}
          ],
          "properties": [
            {
              "name": "server.error.include-message",
              "type": "java.lang.String",
              "deprecated": true,
              "deprecation": {
                "level": "error",
                "replacement": "spring.web.error.include-message",
                "since": "4.0.0"
              }
            },
            {"name": "server.port", "type": "java.lang.Integer"},
            {
              "name": "spring.datasource.dbcp2.max-wait-millis",
              "type": "java.lang.Long",
              "deprecated": true,
              "deprecation": {}
            }
          ]
        }
        """;

    @SneakyThrows
    private static Path archive(Path directory) {
        Path jar = directory.resolve("starter.jar");
        try (OutputStream file = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(file)) {
            zip.putNextEntry(new ZipEntry(ENTRY));
            zip.write(WITHDRAWN.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return jar;
    }

    @SneakyThrows
    private static Path empty(Path directory) {
        Path jar = directory.resolve("plain.jar");
        try (OutputStream file = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(file)) {
            zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zip.closeEntry();
        }
        return jar;
    }

    private static Optional<ConfigurationProperty> named(List<ConfigurationProperty> published, String name) {
        return published.stream().filter(entry -> name.equals(entry.name())).findFirst();
    }

    @Test
    void readsEveryPropertyAndGroupAnArchiveDeclares(@TempDir Path directory) {
        Path jar = archive(directory);

        List<ConfigurationProperty> published = new ConfigurationMetadata(List.of(jar)).published();

        assertEquals(4, published.size(), "three properties and the group above them");
        assertTrue(named(published, "server.error").orElseThrow().group(), "the group is marked as one");
        assertFalse(named(published, "server.port").orElseThrow().group(), "and a property is not");
    }

    @Test
    void carriesWhatTheSupplierSaysAboutAWithdrawnKey(@TempDir Path directory) {
        Path jar = archive(directory);

        ConfigurationProperty.Deprecation stated = named(
            new ConfigurationMetadata(List.of(jar)).published(), "server.error.include-message"
        ).orElseThrow().deprecation();

        assertTrue(stated.unbound(), "the level says the container has stopped reading it");
        assertEquals("spring.web.error.include-message", stated.replacement(), "the replacement is carried");
        assertEquals("4.0.0", stated.since(), "and the release that withdrew it");
    }

    @Test
    void defaultsTheLevelOfADeprecationThatNamesNone(@TempDir Path directory) {
        Path jar = archive(directory);

        ConfigurationProperty.Deprecation stated = named(
            new ConfigurationMetadata(List.of(jar)).published(), "spring.datasource.dbcp2.max-wait-millis"
        ).orElseThrow().deprecation();

        assertTrue(stated.deprecated(), "an empty deprecation still withdraws the key");
        assertFalse(stated.unbound(), "and the specification defaults it to the level that still binds");
    }

    @Test
    void leavesAKeyInGoodStandingWithNothingSaidAboutIt(@TempDir Path directory) {
        Path jar = archive(directory);

        ConfigurationProperty.Deprecation stated = named(
            new ConfigurationMetadata(List.of(jar)).published(), "server.port"
        ).orElseThrow().deprecation();

        assertFalse(stated.deprecated(), "nothing was said, which is different from being said emptily");
    }

    @Test
    void passesOverAnArchiveThatPublishesNoMetadata(@TempDir Path directory) {
        Path jar = empty(directory);

        assertEquals(
            List.of(), new ConfigurationMetadata(List.of(jar)).published(),
            "most jars contribute no settings and are not thereby wrong"
        );
    }

    @Test
    @SneakyThrows
    void readsTheMetadataAModuleWroteIntoItsOwnOutputDirectory(@TempDir Path directory) {
        Path classes = directory.resolve("classes");
        Files.createDirectories(classes.resolve("META-INF"));
        Files.writeString(classes.resolve(ENTRY), WITHDRAWN);

        List<ConfigurationProperty> published = new ConfigurationMetadata(List.of(classes)).published();

        assertTrue(named(published, "server.port").isPresent(), "a project declares its own settings too");
    }

    @Test
    @SneakyThrows
    void passesOverAnOutputDirectoryThatHoldsNoMetadata(@TempDir Path directory) {
        Path classes = Files.createDirectories(directory.resolve("classes"));

        assertEquals(
            List.of(), new ConfigurationMetadata(List.of(classes)).published(),
            "a module that declares no settings of its own writes no such file"
        );
    }

    @Test
    @SneakyThrows
    void refusesAnArchiveOnTheClasspathThatCannotBeRead(@TempDir Path directory) {
        Path broken = Files.writeString(directory.resolve("broken.jar"), "not an archive");
        ConfigurationMetadata metadata = new ConfigurationMetadata(List.of(broken));

        assertThrows(
            UncheckedIOException.class, metadata::published,
            "a resolved artifact that will not open is a broken build rather than an absent contribution"
        );
    }
}
