package eu.ciechanowiec.airness.maven;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Optional;
import java.util.Properties;
import lombok.experimental.UtilityClass;

/**
 * Reads the pins embedded by the producer build, independently of a consumer's effective properties.
 */
@UtilityClass
final class ScannerTools {

    private static final String RESOURCE = "/eu/ciechanowiec/airness/scanners/tools.properties";

    static String image(String tool) {
        URL resource = Optional.ofNullable(ScannerTools.class.getResource(RESOURCE))
            .orElseThrow(() -> new IllegalStateException("Missing packaged scanner pins"));
        try (InputStream input = resource.openStream()) {
            Properties pins = new Properties();
            pins.load(input);
            return image(pins, tool);
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read packaged scanner pins", exception);
        }
    }

    static String image(Properties pins, String tool) {
        return Optional.ofNullable(pins.getProperty(tool))
            .filter(value -> !value.contains("${"))
            .orElseThrow(() -> new IllegalArgumentException("Unknown or unpinned scanner " + tool));
    }
}
