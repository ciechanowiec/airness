package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackageRootsTest {

    private static final int MANY = 400;
    private static final int STACK = 512 * 1024;

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

    /*
     * A fixture quoting another source is as long as the source it quotes, and this reader masks it a
     * token at a time rather than a character at a time. The difference is a stack frame per character
     * against one per token, which is the difference between a reader bounded by the shape of a file and
     * one bounded by its length. The stack is named here rather than inherited, because the length that
     * breaks an inherited one is whatever the machine running the build allows.
     */
    @Test
    @SneakyThrows
    void readsASourceQuotingALongFixture() {
        Files.writeString(
            this.directory.resolve("Example.java"),
            "package com.example;\nclass Example { static final String QUOTED = \"\"\"\n"
                + "package misleading.example;\n".repeat(MANY) + "\"\"\"; }"
        );

        Optional<List<String>> problems = onABoundedStack(
            () -> PackageRoots.problems("com.example", List.of(this.directory))
        );

        assertTrue(problems.isPresent(), "a quoted fixture is masked in runs, so its length costs no depth");
        assertTrue(
            problems.orElseThrow().isEmpty(),
            "and the package it quotes stays quoted, so the file answers for its own declaration alone"
        );
    }

    @SneakyThrows
    private static <T> Optional<T> onABoundedStack(Supplier<T> reader) {
        List<T> answered = new ArrayList<>();
        Thread thread = new Thread(null, () -> answered.add(reader.get()), "bounded-stack", STACK);
        thread.start();
        thread.join();
        return answered.stream().findFirst();
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
