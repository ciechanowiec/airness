package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * The full-verification workflow scaffold runs in a consumer repository, so it can neither read the node
 * harness's manifest nor call this repository's Maven action, and carries its own copies of both pins. Each
 * copy equals the source the self workflows read, so a Playwright or Maven bump can't leave the scaffold
 * pulling a stale image or checking a stale archive.
 */
class ScaffoldPinsTest {

    private static final Path SCAFFOLD = Path.of(
        "airness-assets/src/main/resources/airness/files/node/.github%2Fworkflows%2Fverify-full.yml.asset"
    );
    private static final Pattern SPLIT_IMAGE = Pattern.compile(
        "image='([^']+@sha256:)'\\s+image=\"\\$\\{image}([a-f0-9]{64})\""
    );
    private static final Pattern BROWSER_IMAGE = Pattern.compile("\"browserImage\": \"([^\"]+)\"");
    private static final Pattern CHECKSUM = Pattern.compile("'([a-f0-9]{128})'");

    @Test
    void pullsTheBrowserImageTheNodeHarnessPins() {
        Matcher scaffold = SPLIT_IMAGE.matcher(read(SCAFFOLD));
        Matcher manifest = BROWSER_IMAGE.matcher(read(Path.of("airness-node", "package.json")));
        assertEquals(
            manifest.find() ? List.of(manifest.group(1)) : List.of("no browserImage in airness-node"),
            scaffold.find() ? List.of(scaffold.group(1) + scaffold.group(2)) : List.of("no image in the scaffold"),
            "the scaffold pre-pulls the image the e2e gate connects to"
        );
    }

    @Test
    void checksMavenAgainstTheChecksumOfTheSelfWorkflows() {
        Matcher scaffold = CHECKSUM.matcher(read(SCAFFOLD));
        Matcher action = CHECKSUM.matcher(read(Path.of(".github", "actions", "install-maven", "action.yml")));
        assertEquals(
            action.find() ? List.of(action.group(1)) : List.of("no checksum in the Maven action"),
            scaffold.find() ? List.of(scaffold.group(1)) : List.of("no checksum in the scaffold"),
            "the scaffold installs the Maven archive the self workflows install"
        );
    }

    @SneakyThrows
    private static String read(Path relative) {
        return Files.readString(SelfModules.repository().resolve(relative));
    }
}
