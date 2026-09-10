package eu.ciechanowiec.airness.spring;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Set;

record SpringStreamingInput(String kind, String name, String origin, String detail) {

    private static final String JAR = "jar:";
    private static final Set<String> KINDS = Set.of("source", "type", "resource");

    static SpringStreamingInput parse(String line) {
        String[] fields = line.split(" ", -1);
        if (fields.length != 4 || !KINDS.contains(decode(fields[0]))) {
            throw new IllegalArgumentException("Malformed streaming production input");
        }
        return new SpringStreamingInput(decode(fields[0]), decode(fields[1]), decode(fields[2]), decode(fields[3]));
    }

    boolean at(String address) {
        return canonical(this.origin).equals(canonical(address));
    }

    private static String canonical(String address) {
        if (address.startsWith(JAR)) {
            int boundary = address.indexOf("!/");
            return "jar:" + canonical(address.substring(4, boundary)) + address.substring(boundary);
        }
        URI uri = URI.create(address).normalize();
        URI canonical = "file".equals(uri.getScheme()) ? Path.of(uri).toUri() : uri;
        return canonical.toString();
    }

    static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
