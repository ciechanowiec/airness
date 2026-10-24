package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * The npm package {@code @airness/node} carries the version of the Airness release it belongs to, which is
 * the version the wiring of a node project compares it with.
 */
class NodePackageVersionTest {

    private static final Pattern VERSION = Pattern.compile("^ {2}\"version\": \"([^\"]+)\",$", Pattern.MULTILINE);

    @Test
    @SneakyThrows
    void carriesTheVersionOfTheReactor() {
        String manifest = Files.readString(SelfModules.repository().resolve("airness-node").resolve("package.json"));
        Matcher matcher = VERSION.matcher(manifest);
        assertEquals(
            List.of(Xml.text(ProjectFiles.document(ProjectFiles.rootPom()), "version").orElseThrow()),
            matcher.find() ? List.of(matcher.group(1)) : List.of(),
            "a release that raises the poms raises airness-node/package.json with them"
        );
    }
}
