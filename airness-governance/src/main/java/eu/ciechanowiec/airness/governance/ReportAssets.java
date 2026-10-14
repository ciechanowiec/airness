package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Bundled screen and print resources, embedded without external requests.
 */
@UtilityClass
final class ReportAssets {

    private static final String ROOT = "/eu/ciechanowiec/airness/compliance/";

    static String styles() {
        return Stream.of(
            "Source Serif 4|source-serif-4|normal", "Source Serif 4|source-serif-4|italic",
            "Playfair Display|playfair-display|normal", "Source Code Pro|source-code-pro|normal"
        ).map(ReportAssets::font).collect(Collectors.joining()) + text("report.css");
    }

    static String script() {
        return text("report.js");
    }

    static String credits() {
        return "<h3>Report typefaces</h3>" + Stream.of("source-serif-4", "playfair-display", "source-code-pro")
            .map(
                name -> "<details><summary>" + name + " (Fontsource 5.3.0)</summary><pre>"
                    + ReportMarkup.escape(text(name + "-LICENSE.txt")) + "</pre></details>"
            ).collect(Collectors.joining());
    }

    private static String font(String declaration) {
        String[] parts = declaration.split("\\|", -1);
        String family = parts[0];
        String name = parts[1];
        String style = parts[2];
        String data = Base64.getEncoder().encodeToString(bytes(name + "-latin-wght-" + style + ".woff2"));
        return "@font-face{font-family:'" + family + "';font-style:" + style
            + ";font-weight:200 900;font-display:block;src:url(data:font/woff2;base64," + data + ") format('woff2')}";
    }

    private static String text(String name) {
        return new String(bytes(name), StandardCharsets.UTF_8);
    }

    private static byte[] bytes(String name) {
        URL resource = Optional.ofNullable(ReportAssets.class.getResource(ROOT + name))
            .orElseThrow(() -> new IllegalStateException("Missing report resource " + name));
        try (InputStream stream = resource.openStream()) {
            return stream.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read report resource " + name, exception);
        }
    }
}
