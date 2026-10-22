package eu.ciechanowiec.airness.maven;

import com.microsoft.playwright.Playwright;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import lombok.experimental.UtilityClass;

/**
 * The report renderer's browser cache, isolated from other {@link Playwright} installations and versions.
 */
@UtilityClass
final class ReportBrowser {

    private static final String METADATA = "/META-INF/maven/com.microsoft.playwright/playwright/pom.properties";

    static Map<String, String> environment() {
        Path directory = Path.of(System.getProperty("user.home"), ".cache", "airness", "report-browser", version());
        return Map.of(
            "PLAYWRIGHT_BROWSERS_PATH", directory.toAbsolutePath().normalize().toString(),
            "PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1"
        );
    }

    private static String version() {
        URL metadata = Optional.ofNullable(Playwright.class.getResource(METADATA))
            .orElseThrow(() -> new IllegalStateException("Missing pinned Playwright metadata"));
        try (InputStream stream = metadata.openStream()) {
            Properties properties = new Properties();
            properties.load(stream);
            return Objects.requireNonNull(properties.getProperty("version"), "Missing pinned Playwright version");
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read pinned Playwright metadata", exception);
        }
    }
}
