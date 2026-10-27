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
 * the version the wiring of a node project compares it with. Its browser image matches its own Playwright,
 * independently of the Java Playwright that renders compliance reports.
 */
class NodePackageVersionTest {

    private static final Pattern PLAYWRIGHT = Pattern.compile(
        "^ {4}\"@playwright/test\": \"([^\"]+)\",?$", Pattern.MULTILINE
    );
    private static final Pattern BROWSER_IMAGE = Pattern.compile(
        "\"browserImage\": \"mcr\\.microsoft\\.com/playwright:v([^\"@]+)-noble@sha256:[a-f0-9]{64}\""
    );
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
