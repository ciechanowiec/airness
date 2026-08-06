package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import net.revelc.code.formatter.java.JavaFormatter;
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
}
