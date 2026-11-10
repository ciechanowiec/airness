package com.example.starter.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * Holds the audit trail to its tests. An action added to {@link AuditAction} and written by the
 * application with no test reading it back would leave the trail claiming a record nobody proved is
 * made, so every action has to be asked of {@link Entries#written} by some test of this suite.
 */
class AuditActionTest {

    // The tests run from the directory of the project, where Maven keeps the sources it compiled them from.
    private static final Path TESTS = Path.of("src", "test", "java");

    private static final Pattern ASSERTED = Pattern.compile("\\.written\\(\\s*AuditAction\\.(\\w+)");

    @SneakyThrows
    private static Set<String> asserted() {
        try (Stream<Path> files = Files.walk(TESTS)) {
            return files.filter(file -> file.toString().endsWith(".java"))
                .map(AuditActionTest::read)
                .flatMap(source -> ASSERTED.matcher(source).results().map(match -> match.group(1)))
                .collect(Collectors.toCollection(TreeSet::new));
        }
    }

    @SneakyThrows(IOException.class)
    private static String read(Path file) {
        return Files.readString(file);
    }

    @Test
    void everyActionIsAssertedAsWrittenBySomeTest() {
        Set<String> asserted = asserted();
        List<String> unasserted = Arrays.stream(AuditAction.values())
            .map(AuditAction::name)
            .filter(name -> !asserted.contains(name))
            .toList();
        assertEquals(List.of(), unasserted);
    }
}
