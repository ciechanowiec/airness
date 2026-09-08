package eu.ciechanowiec.airness.governance;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * One literal message lookup in a production template.
 *
 * @param resource the classpath resource name
 * @param location the repository path, line and column
 * @param key      the literal message name
 */
public record TemplateMessageReference(String resource, String location, String key) {

    /**
     * Encodes a reference without exposing delimiters carried by a file name.
     *
     * @return the manifest line payload
     */
    public String encoded() {
        return encode(this.resource) + " " + encode(this.location) + " " + encode(this.key);
    }

    static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
