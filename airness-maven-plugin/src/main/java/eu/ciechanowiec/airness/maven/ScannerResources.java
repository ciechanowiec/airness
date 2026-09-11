package eu.ciechanowiec.airness.maven;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import lombok.experimental.UtilityClass;

/**
 * Materializes only programs and policy carried by the installed harness.
 */
@UtilityClass
final class ScannerResources {

    private static final String ROOT = "/eu/ciechanowiec/airness/scanners/";

    static Path copy(String name, Path output) throws IOException {
        URL resource = Optional.ofNullable(ScannerResources.class.getResource(ROOT + name))
            .orElseThrow(() -> new IOException("Missing packaged scanner resource " + name));
        Path file = output.resolve(name);
        try (InputStream input = resource.openStream()) {
            Files.copy(input, file);
        }
        return file;
    }
}
