package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.ciechanowiec.airness.governance.LicenseExpression;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * Reads an npm license declaration the way the one license allowlist judges it: by SPDX identifiers only.
 *
 * <p>The allowlist's merges carry the free-text names Maven poms write, such as {@code BSD} or
 * {@code Public domain}. An npm package declares an SPDX expression, and the same text there is ambiguous, so
 * a declaration that names no SPDX license is never matched through a merge: it stays a name no allowlist entry
 * admits. An identifier is matched regardless of case, the way the registries write it, and the deprecated
 * {@code X+} as {@code X}. {@code X WITH <exception>} is judged by {@code X} when the exception is on the SPDX
 * exception list, because an exception only grants more than its license does.
 */
@UtilityClass
final class SpdxLicenses {

    private static final String LICENSES = "/licenses/licenses.json";
    private static final String EXCEPTIONS = "/eu/ciechanowiec/airness/spdx-exceptions.txt";
    private static final String UNKNOWN = "not an SPDX license: ";
    private static final Map<String, String> IDENTIFIERS = identifiers();
    private static final Map<String, String> EXCEPTION_IDS = exceptions();

    /**
     * The alternatives an npm declaration offers, each license written as its SPDX identifier.
     *
     * @param declared the declared license expression
     * @return the alternatives the allowlist judges
     */
    static List<String> alternatives(String declared) {
        return LicenseExpression.alternatives(declared, SpdxLicenses::spelling, SpdxLicenses::exception);
    }

    /**
     * The SPDX spelling of one license identifier, or a name no allowlist entry admits when it is none.
     *
     * @param identifier a license identifier as declared
     * @return the canonical SPDX identifier
     */
    static String spelling(String identifier) {
        String bare = identifier.endsWith("+") ? identifier.substring(0, identifier.length() - 1) : identifier;
        return Optional.ofNullable(IDENTIFIERS.get(identifier.toLowerCase(Locale.ROOT)))
            .or(() -> Optional.ofNullable(IDENTIFIERS.get(bare.toLowerCase(Locale.ROOT))))
            .orElse(UNKNOWN + identifier);
    }

    /**
     * Whether an exception is on the SPDX exception list.
     *
     * @param exception the exception identifier as declared
     * @return whether it is a known exception
     */
    static boolean exception(String exception) {
        return EXCEPTION_IDS.containsKey(exception.toLowerCase(Locale.ROOT));
    }

    private static Map<String, String> identifiers() {
        try (InputStream stream = SpdxLicenses.class.getResourceAsStream(LICENSES)) {
            JsonNode list = new ObjectMapper().readTree(
                Optional.ofNullable(stream).orElseThrow(() -> new IllegalStateException("No SPDX license list"))
            );
            return list.path("licenses").valueStream().map(license -> license.path("licenseId").asText())
                .collect(Collectors.toUnmodifiableMap(id -> id.toLowerCase(Locale.ROOT), Function.identity()));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the SPDX license list", exception);
        }
    }

    private static Map<String, String> exceptions() {
        try (InputStream stream = SpdxLicenses.class.getResourceAsStream(EXCEPTIONS)) {
            String text = new String(
                Optional.ofNullable(stream).orElseThrow(() -> new IllegalStateException("No SPDX exception list"))
                    .readAllBytes(),
                StandardCharsets.UTF_8
            );
            return text.lines().map(String::strip).filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .collect(Collectors.toUnmodifiableMap(id -> id.toLowerCase(Locale.ROOT), Function.identity()));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the SPDX exception list", exception);
        }
    }
}
