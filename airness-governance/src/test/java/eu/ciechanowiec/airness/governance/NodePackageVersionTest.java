package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * Each npm package this reactor builds carries the version of the Airness release it belongs to, which is
 * the version the wiring of a node project compares it with. Its browser image matches its own Playwright,
 * independently of the Java Playwright that renders compliance reports.
 */
class NodePackageVersionTest {

    private static final Pattern PLAYWRIGHT = Pattern.compile(
        "^ {4}\"@playwright/test\": \"([^\"]+)\",?$", Pattern.MULTILINE
    );
    private static final Pattern BROWSER_IMAGE = Pattern.compile(
        "\"browserImage\": \"mcr\\.microsoft\\.com/playwright:v([^\"@]+)-resolute@sha256:[a-f0-9]{64}\""
    );
    private static final Pattern VERSION = Pattern.compile("^ {2}\"version\": \"([^\"]+)\",$", Pattern.MULTILINE);

    @Test
    void carriesTheVersionOfTheReactor() {
        String reactor = Xml.text(ProjectFiles.document(ProjectFiles.rootPom()), "version").orElseThrow();
        assertEquals(
            List.of(),
            Stream.of("airness-node", "airness-payload-node")
                .filter(module -> !reactor.equals(declaredVersion(module)))
                .toList(),
            "a release that raises the poms raises the package.json of every npm package with them"
        );
    }

    @SneakyThrows
    private static String declaredVersion(String module) {
        Matcher matcher = VERSION.matcher(
            Files.readString(SelfModules.repository().resolve(module).resolve("package.json"))
        );
        return matcher.find() ? matcher.group(1) : module + " declares no version";
    }

    @Test
    @SneakyThrows
    void pinsAnImmutableBrowserImageMatchingTheNodePlaywright() {
        String manifest = Files.readString(SelfModules.repository().resolve("airness-node").resolve("package.json"));
        Matcher npm = PLAYWRIGHT.matcher(manifest);
        Matcher image = BROWSER_IMAGE.matcher(manifest);
        assertEquals(
            npm.find() ? List.of(npm.group(1)) : List.of("no @playwright/test in airness-node"),
            image.find() ? List.of(image.group(1)) : List.of("no immutable browser image in airness-node"),
            "the browser sweep connects to the image of its own Playwright; the Java report renderer is independent"
        );
    }
}
