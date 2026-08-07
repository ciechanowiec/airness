package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackageRootsTest {

    @TempDir
    private Path directory;

    @Test
    @SneakyThrows
    void acceptsEveryPackageBeneathTheConfiguredPrefix() {
        Files.writeString(
            this.directory.resolve("Example.java"),
            "/* package misleading.example;\nmore fixture text */\npackage com.example.orders; class Example {}"
        );
        Files.writeString(this.directory.resolve("Direct.java"), "package com.example; class Direct {}");
        Files.writeString(this.directory.resolve("module-info.java"), "module com.example {}");

        assertTrue(PackageRoots.problems("com.example", List.of(this.directory)).isEmpty());
    }

    @Test
    @SneakyThrows
    void rejectsAPrefixThatWouldDisableNullAway() {
        Files.writeString(this.directory.resolve("Example.java"), "package com.example; class Example {}");

        assertFalse(PackageRoots.problems("wrong.base", List.of(this.directory)).isEmpty());
    }

    @Test
    void rejectsAnInvalidJavaPackageName() {
        assertFalse(PackageRoots.problems("wrong-package", List.of(this.directory)).isEmpty());
    }

    @Test
    @SneakyThrows
    void rejectsASourceInTheUnnamedPackage() {
        Files.writeString(this.directory.resolve("Example.java"), "class Example {}");

        assertFalse(PackageRoots.problems("com.example", List.of(this.directory)).isEmpty());
    }
}
