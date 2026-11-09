package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StarterTemplateTest {

    private static final String POM = "pom.xml.fixture";

    @TempDir
    private Path directory;

    @Test
    @SneakyThrows
    void opensAStarterPackedInAJar() {
        Path jar = this.directory.resolve("starters with space.jar");
        try (JarOutputStream archive = new JarOutputStream(Files.newOutputStream(jar))) {
            write(archive, StarterTemplate.LOCATION + "sample/" + POM, "<project/>");
            write(archive, StarterTemplate.LOCATION + "sample/src/Main.java", "class Main {}");
        }
        try (
            URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null);
            StarterTemplate starter = StarterTemplate.open(loader, "sample")
        ) {
            assertEquals("<project/>", Files.readString(starter.root().resolve(POM)));
            assertEquals("class Main {}", Files.readString(starter.root().resolve("src/Main.java")));
        }
    }

    @Test
    @SneakyThrows
    void opensAStarterLaidOutInADirectory() {
        Path starterDirectory = Files.createDirectories(this.directory.resolve(StarterTemplate.LOCATION + "sample"));
        Files.writeString(starterDirectory.resolve(POM), "<project/>");
        try (
            URLClassLoader loader = new URLClassLoader(new URL[] {this.directory.toUri().toURL()}, null);
            StarterTemplate starter = StarterTemplate.open(loader, "sample")
        ) {
            assertEquals(starterDirectory.toRealPath(), starter.root().toRealPath());
        }
    }

    @Test
    @SneakyThrows
    void refusesAStarterThatDoesNotExist() {
        try (URLClassLoader loader = new URLClassLoader(new URL[] {this.directory.toUri().toURL()}, null)) {
            IOException refusal = assertThrows(
                IOException.class, () -> StarterTemplate.open(loader, "missing")
            );
            String message = String.valueOf(refusal.getMessage());
            assertTrue(message.contains("no starter named missing"), message);
        }
    }

    @SneakyThrows
    private static void write(JarOutputStream archive, String entry, String text) {
        archive.putNextEntry(new JarEntry(entry));
        archive.write(text.getBytes(StandardCharsets.UTF_8));
        archive.closeEntry();
    }
}
