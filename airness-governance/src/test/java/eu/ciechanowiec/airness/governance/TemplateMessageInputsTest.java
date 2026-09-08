package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplateMessageInputsTest {

    @TempDir
    private Path root;

    @Test
    void fingerprintsTheInventoryAndSeparatelyMatchesTheBuildStart() {
        TemplateMessageReference first = new TemplateMessageReference("templates/a.html", "a.html:1:1", "alpha");
        TemplateMessageReference second = new TemplateMessageReference("templates/b.html", "b.html:1:1", "beta");
        TemplateMessageInputs inputs = new TemplateMessageInputs(
            1, List.of("example.Application"), List.of(second, first)
        );
        Path file = TemplateMessageInputs.beside(this.root.resolve("context.evidence"));
        assertFalse(inputs.matches(file));
        inputs.write(file);
        assertTrue(inputs.matches(file));
        TemplateMessageInputs reordered = new TemplateMessageInputs(2, inputs.applications(), List.of(first, second));
        assertFalse(reordered.matches(file));
        assertEquals(inputs.digest(), reordered.digest());
        assertNotEquals(inputs.digest(), new TemplateMessageInputs(1, inputs.applications(), List.of(first)).digest());
    }

    @Test
    @SneakyThrows
    void refusesUnreadableManifestsAndFailedWrites() {
        TemplateMessageInputs inputs = new TemplateMessageInputs(1, List.of(), List.of());
        Path malformed = this.root.resolve("malformed");
        byte invalid = -1;
        Files.write(malformed, new byte[] {invalid});
        assertThrows(UncheckedIOException.class, () -> inputs.matches(malformed));
        assertThrows(UncheckedIOException.class, () -> inputs.write(this.root));
    }
}
