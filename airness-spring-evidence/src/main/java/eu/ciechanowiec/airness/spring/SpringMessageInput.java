package eu.ciechanowiec.airness.spring;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * A reference supplied by the Maven-side template reader.
 *
 * @param resource the classpath resource
 * @param location its source location
 * @param key      the literal key
 */
record SpringMessageInput(String resource, String location, String key) {

    static SpringMessageInput parse(String line) {
        String[] parts = line.split(" ", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid template message reference");
        }
        return new SpringMessageInput(decode(parts[0]), decode(parts[1]), decode(parts[2]));
    }

    String encoded() {
        return encode(this.resource) + " " + encode(this.location) + " " + encode(this.key);
    }

    static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
