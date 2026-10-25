package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A stack layer adds suppression forms to the node ceiling through its own jar, and a layer that declares none
 * adds nothing.
 */
class SuppressionFormsTest {

    @Test
    @SneakyThrows
    void readsTheFormsOfTheLayersPresent(@TempDir Path jar) {
        Path declarations = Files.createDirectories(jar.resolve("airness/suppressions"));
        Files.writeString(declarations.resolve("payload.txt"), "# the Payload markers\npayload-ignore\n\n");
        try (URLClassLoader classes = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
            SuppressionForms forms = new SuppressionForms(classes);
            assertEquals(
                List.of("payload-ignore"),
                forms.forms(Set.of(Capability.PAYLOAD, Capability.NODE)).stream().map(Pattern::pattern).toList()
            );
            assertEquals(List.of(), forms.forms(Set.of(Capability.JAVA)));
        }
    }
}
