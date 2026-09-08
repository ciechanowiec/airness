package eu.ciechanowiec.airness.spring;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpringMessageInputsTest {

    private static final String DIGEST = "digest " + "a".repeat(64);
    @TempDir
    private Path root;

    @Test
    @SneakyThrows
    void refusesMissingDuplicateAndMalformedManifestFields() {
        Path file = this.root.resolve("inputs");
        assertThrows(UncheckedIOException.class, () -> SpringMessageInputs.read(file));
        Files.write(file, List.of("started 1", "digest wrong"));
        assertThrows(IllegalArgumentException.class, () -> SpringMessageInputs.read(file));
        Files.write(file, List.of("started 1", "started 2", DIGEST));
        assertThrows(IllegalArgumentException.class, () -> SpringMessageInputs.read(file));
        Files.write(file, List.of("started 1", DIGEST, "reference missing-parts"));
        assertThrows(IllegalArgumentException.class, () -> SpringMessageInputs.read(file));
    }
}
