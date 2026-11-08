package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import net.revelc.code.formatter.java.JavaFormatter;
import net.revelc.code.impsort.ImpSort;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StarterCopyTest {

    private static final String VERSION = "9.8.7";
    private static final StarterRequest REQUEST = StarterRequest.of(
        "org.sample", "task-tracker", Optional.of("org.sample.tracker"), Optional.of("Task Tracker")
    );

    @TempDir
    private Path directory;

    @Test
    void movesTheCodeToThePackageAndDropsTheFixtureSuffix() {
        assertEquals(
            "src/main/java/org/sample/tracker/web/Htmx.java",
            StarterCopy.target("src/main/java/com/example/starter/web/Htmx.java", REQUEST)
        );
        assertEquals("pom.xml", StarterCopy.target("pom.xml.fixture", REQUEST));
        assertEquals(
            "tests/e2e/task-tracker.e2e.spec.ts", StarterCopy.target("tests/e2e/starter.e2e.spec.ts", REQUEST)
        );
    }

    @Test
    void renamesTheApplicationButNotASpringBootStarter() {
        String text = """
            package com.example.starter.web;
            name: starter
            COPY target/starter-*.jar application.jar
            <title>Starter</title>
            spring-boot-starter-web
            starters and Starters
            """;

        assertEquals(
            """
                package org.sample.tracker.web;
                name: task-tracker
                COPY target/task-tracker-*.jar application.jar
                <title>Task Tracker</title>
                spring-boot-starter-web
                starters and Starters
                """,
            StarterCopy.content("src/Main.java", text, REQUEST, VERSION)
        );
    }

    @Test
    void fillsThePlaceholdersOfThePomAndLeavesItsProseAlone() {
        String pom = """
            <version>@AIRNESS_VERSION@</version>
            <groupId>@GROUP_ID@</groupId>
            <artifactId>@ARTIFACT_ID@</artifactId>
            <airness.package.root>@PACKAGE@</airness.package.root>
            <!-- arrives through a starter -->
            """;

        assertEquals(
            """
                <version>9.8.7</version>
                <groupId>org.sample</groupId>
                <artifactId>task-tracker</artifactId>
                <airness.package.root>org.sample.tracker</airness.package.root>
                <!-- arrives through a starter -->
                """,
            StarterCopy.content("pom.xml.fixture", pom, REQUEST, VERSION)
        );
    }

    @Test
    @SneakyThrows
    void writesEveryFileOfTheStarterUnderTheApplicationsNames() {
        try (StarterTemplate starter = StarterTemplate.open(StarterCopyTest.class.getClassLoader(), "spring-boot")) {
            StarterCopy.write(starter.root(), this.directory, REQUEST, VERSION);
        }

        assertTrue(
            Files.readString(this.directory.resolve("pom.xml")).contains("<artifactId>task-tracker</artifactId>")
        );
        assertTrue(Files.readString(this.directory.resolve("package.json")).contains("\"@airness/node\": \"9.8.7\""));
        assertTrue(Files.isRegularFile(this.directory.resolve("src/main/java/org/sample/tracker/Application.java")));
        assertTrue(Files.isRegularFile(this.directory.resolve(".gitignore")));
        assertTrue(Files.readString(this.directory.resolve("compose.yaml")).contains("services:"));
        assertFalse(Files.exists(this.directory.resolve("src/main/java/com")));
        try (Stream<Path> files = Files.walk(this.directory)) {
            assertEquals(
                0L, files.filter(Files::isRegularFile).filter(StarterCopyTest::mentionsTheStarter).count(),
                "no file keeps the starter's package or a placeholder"
            );
        }
    }

    @Test
    @SneakyThrows
    void anApplicationNormalizedAfterTheCopyMeetsTheFormattingItIsJudgedBy() {
        try (StarterTemplate starter = StarterTemplate.open(StarterCopyTest.class.getClassLoader(), "spring-boot")) {
            StarterCopy.write(starter.root(), this.directory, REQUEST, VERSION);
        }
        List<Path> roots = List.of(this.directory.resolve("src/main/java"), this.directory.resolve("src/test/java"));
        String target = this.directory.resolve("target").toString();

        SourceFormattingMojo.normalize(roots, new SystemStreamLog(), target);

        JavaFormatter formatter = SourceFormattingMojo.formatter(new SystemStreamLog(), target);
        ImpSort sorter = SourceFormattingMojo.sorter();
        Predicate<Path> judged = source -> SourceFormattingMojo.formatted(formatter, source)
            && SourceFormattingMojo.sorted(sorter, source);
        assertEquals(
            List.of(), SourceFormattingMojo.javaSources(roots).stream().filter(judged.negate()).toList(),
            "every Java source of the application is formatted and its imports sorted"
        );
    }

    @SneakyThrows
    private static boolean mentionsTheStarter(Path file) {
        String text = Files.readString(file);
        return text.contains("com.example.starter") || text.contains("@AIRNESS_VERSION@") || text.contains(
            "@GROUP_ID@"
        );
    }
}
