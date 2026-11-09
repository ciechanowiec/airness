package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Playwright;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * The Java {@link Playwright} that renders compliance reports and the npm {@code @playwright/test} that drives
 * every node gate run the same Chromium build, so a page measured by one stack renders the same in the other.
 *
 * <p>Every release fixes its browser builds in the {@code browsers.json} that {@code playwright-core} ships, and
 * the Java driver bundles a {@code playwright-core} release of its own. Two stacks that resolve the
 * same {@code playwright-core} release therefore run the same Chromium build, whatever their own version numbers
 * say. The npm side's {@code browsers.json} can't be read here, because the reactor builds this module before it
 * installs any npm package, so the proof compares the releases, which every lockfile records.
 */
class PlaywrightCoreAgreementTest {

    private static final String DRIVER_PACKAGE = "/driver/package/";
    private static final String CORE = "playwright-core";
    private static final String LOCKFILE = "pnpm-lock.yaml";

    @Test
    void resolvesTheCoreTheJavaDriverBundlesInEveryNpmPackage() {
        String bundled = driverFile("package.json").path("version").asText();
        String chromium = StreamSupport.stream(driverFile("browsers.json").path("browsers").spliterator(), false)
            .filter(browser -> "chromium".equals(browser.path("name").asText()))
            .map(browser -> browser.path("browserVersion").asText())
            .findFirst()
            .orElse("an unnamed build");
        List<Path> npmPackages = SelfModules.directories().stream()
            .filter(module -> !resolved(module).isEmpty())
            .toList();
        assertFalse(npmPackages.isEmpty(), "a check that read no lockfile proves nothing about this repository");
        assertEquals(
            npmPackages.stream().map(module -> described(module, List.of(bundled))).toList(),
            npmPackages.stream().map(module -> described(module, resolved(module))).toList(),
            "the Java driver bundles " + CORE + " " + bundled + " with Chromium " + chromium + "; raise "
                + "playwright.version in airness-maven-plugin and @playwright/test in each npm package together"
        );
    }

    @SneakyThrows
    private static JsonNode driverFile(String name) {
        try (
            InputStream stream = Optional.ofNullable(Playwright.class.getResourceAsStream(DRIVER_PACKAGE + name))
                .orElseThrow(() -> new IllegalStateException("the Playwright driver ships no " + name))
        ) {
            return new ObjectMapper().readTree(stream);
        }
    }

    private static String described(Path module, List<String> releases) {
        return module.getFileName() + " resolves " + CORE + " " + releases;
    }

    // Every reactor module with a lockfile that resolves the core is read, so a new npm package is held too.
    @SneakyThrows
    private static List<String> resolved(Path module) {
        Path lockfile = module.resolve(LOCKFILE);
        Object document = Files.isRegularFile(lockfile)
            ? new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(lockfile)) : Map.of();
        Map<?, ?> packages = document instanceof Map<?, ?> root && root.get("packages") instanceof Map<?, ?> listed
            ? listed : Map.of();
        return packages.keySet().stream()
            .map(String::valueOf)
            .filter(key -> key.startsWith(CORE + "@"))
            .map(key -> key.substring(CORE.length() + 1).replaceFirst("\\(.*", ""))
            .distinct()
            .toList();
    }
}
