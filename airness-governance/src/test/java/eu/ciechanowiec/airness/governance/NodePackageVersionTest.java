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
 * the version the wiring of a node project compares it with, and the Playwright of the Maven plugin.
 */
class NodePackageVersionTest {

    private static final Pattern PLAYWRIGHT = Pattern.compile(
        "^ {4}\"@playwright/test\": \"([^\"]+)\",?$", Pattern.MULTILINE
    );
    private static final Pattern MAVEN_PLAYWRIGHT = Pattern.compile(
        "<playwright\\.version>([^<]+)</playwright\\.version>"
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
    void pinsThePlaywrightOfTheMavenPluginSoTheEndToEndGateFindsItsBrowser() {
        String manifest = Files.readString(SelfModules.repository().resolve("airness-node").resolve("package.json"));
        Matcher npm = PLAYWRIGHT.matcher(manifest);
        String plugin = Files.readString(
            SelfModules.repository().resolve("airness-maven-plugin").resolve("pom.xml")
        );
        Matcher maven = MAVEN_PLAYWRIGHT.matcher(plugin);
        assertEquals(
            maven.find() ? List.of(maven.group(1)) : List.of("no playwright.version in the plugin"),
            npm.find() ? List.of(npm.group(1)) : List.of("no @playwright/test in airness-node"),
            "the e2e gate drives the Chromium report-browser-install installs for the plugin's Playwright, which is"
                + " the browser only the same npm Playwright finds"
        );
    }
}
