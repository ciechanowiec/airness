package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import net.revelc.code.formatter.java.JavaFormatter;
import net.revelc.code.impsort.ImpSort;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceFormattingMojoTest {

    @Test
    @SneakyThrows
    void treatsTheFormatterUnchangedSentinelAsTheOriginalSource(
        @TempDir Path directory
    ) {
        String held = "package example;\n\nfinal class Example {\n}\n";
        Path source = directory.resolve("Example.java");
        Files.writeString(source, held);
        JavaFormatter formatter = SourceFormattingMojo.formatter(
            new SystemStreamLog(), directory.resolve("target").toString()
        );
        assertTrue(SourceFormattingMojo.formatted(formatter, source));
        assertEquals(held, SourceFormattingMojo.formattedSource(formatter, source));
    }

    @Test
    @SneakyThrows
    void readsImportOrderTheWayTheGoalReportsIt(
        @TempDir Path directory
    ) {
        Path ordered = directory.resolve("Ordered.java");
        Files.writeString(ordered, source("Ordered", "import java.util.List;\nimport java.util.Map;\n"));
        Path shuffled = directory.resolve("Shuffled.java");
        Files.writeString(shuffled, source("Shuffled", "import java.util.Map;\nimport java.util.List;\n"));
        ImpSort sorter = SourceFormattingMojo.sorter();
        assertTrue(SourceFormattingMojo.sorted(sorter, ordered));
        assertFalse(SourceFormattingMojo.sorted(sorter, shuffled));
    }

    @Test
    @SneakyThrows
    void finishesTheConditionalParameterAnnotationLayout(@TempDir Path directory) {
        String held = """
            package example;

            final class Example {

                void update(
                    @PathVariable(name = "reference", required = true) String reference,
                    @ModelAttribute(name = "form", binding = true)
                    @Valid BookingForm bookingForm
                ) {
                }
            }
            """;
        String expected = """
            package example;

            final class Example {

                void update(
                    @PathVariable(name = "reference", required = true) String reference,
                    @ModelAttribute(name = "form", binding = true)
                    @Valid
                    BookingForm bookingForm
                ) {
                }
            }
            """;
        Path source = directory.resolve("Example.java");
        Files.writeString(source, held);
        JavaFormatter formatter = SourceFormattingMojo.formatter(
            new SystemStreamLog(), directory.resolve("target").toString()
        );

        assertEquals(expected, SourceFormattingMojo.formattedSource(formatter, source));
        Files.writeString(source, expected);
        assertTrue(SourceFormattingMojo.formatted(formatter, source));
    }

    @Test
    @SneakyThrows
    void keepsAShortMultipleAnnotationParameterCompact(@TempDir Path directory) {
        String held = """
            package example;

            final class Example {

                void update(@First @Second Type value) {
                }
            }
            """;
        Path source = directory.resolve("Example.java");
        Files.writeString(source, held);
        JavaFormatter formatter = SourceFormattingMojo.formatter(
            new SystemStreamLog(), directory.resolve("target").toString()
        );

        assertEquals(held, SourceFormattingMojo.formattedSource(formatter, source));
    }

    private static String source(String name, String imports) {
        return "package example;\n\n" + imports + "\nfinal class " + name + " {\n\n"
            + "    List<Map<String, String>> held() {\n        return List.of();\n    }\n}\n";
    }
}
