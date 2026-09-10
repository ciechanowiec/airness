package eu.ciechanowiec.airness.governance;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Stream;

/**
 * A production input used to establish streaming configuration provenance.
 *
 * @param kind   source, type or resource
 * @param name   the source location or qualified type
 * @param origin the production runtime location
 * @param detail the content fingerprint or callback classification
 */
public record StreamingInput(String kind, String name, String origin, String detail) {

    /**
     * Encodes fields without exposing configuration values.
     *
     * @return the stable manifest line
     */
    public String encoded() {
        return String.join(
            " ", Stream.of(this.kind, this.name, this.origin, this.detail)
                .map(StreamingInput::encode).toList()
        );
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
