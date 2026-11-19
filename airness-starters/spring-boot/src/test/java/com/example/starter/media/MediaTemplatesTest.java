package com.example.starter.media;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

class MediaTemplatesTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    private static final Path FRAGMENT = TEMPLATES.resolve("components/media.html");

    private static String read(Path template) {
        try {
            return Files.readString(template);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    // A stored file is drawn from its renditions, so a template naming an address under /media/ itself would draw
    // a file past the fragment that hands the browser the renditions with the size the picture takes.
    @Test
    @SneakyThrows
    void drawsAStoredFileOnlyThroughTheMediaFragment() {
        try (Stream<Path> templates = Files.walk(TEMPLATES)) {
            List<Path> offenders = templates.filter(path -> path.toString().endsWith(".html"))
                .filter(path -> !path.equals(FRAGMENT))
                .filter(path -> read(path).contains("/media/"))
                .toList();
            assertEquals(List.of(), offenders);
        }
    }
}
