package eu.ciechanowiec.airness.maven;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A package URL that names a report package outside Maven: an npm package, or a pinned build tool.
 *
 * <p>The report names a Maven artifact by its coordinate and anything else by its package URL, so one
 * inventory can hold both. A {@code checksum} qualifier, which the pinned toolchain carries, becomes the
 * component's hash in the SBOM.
 *
 * @param text      the whole package URL
 * @param namespace the decoded namespace, such as an npm scope, empty when there is none
 * @param name      the decoded name
 * @param version   the version, empty when there is none
 * @param sha256    the SHA-256 the {@code checksum} qualifier names, empty when there is none
 */
record PackageUrl(String text, String namespace, String name, String version, String sha256) {

    private static final Pattern SHAPE = Pattern.compile(
        "^pkg:[a-z0-9.+-]+/(?:(?<namespace>[^/@?]+)/)?(?<name>[^/@?]+)(?:@(?<version>[^?]+))?(?:\\?(?<qualifiers>.*))?$"
    );
    private static final Pattern CHECKSUM = Pattern.compile("(?:^|&)checksum=sha256:(?<hash>[0-9a-f]{64})(?:&|$)");

    /**
     * Reads a package URL.
     *
     * @param text the package URL
     * @return the parts it names
     * @throws IllegalArgumentException when the text is no package URL
     */
    static PackageUrl parse(String text) {
        Matcher match = SHAPE.matcher(text);
        if (!match.matches()) {
            throw new IllegalArgumentException("Not a package URL: " + text);
        }
        String qualifiers = Optional.ofNullable(match.group("qualifiers")).orElse("");
        Matcher checksum = CHECKSUM.matcher(qualifiers);
        return new PackageUrl(
            text, decoded(Optional.ofNullable(match.group("namespace")).orElse("")), decoded(match.group("name")),
            Optional.ofNullable(match.group("version")).orElse(""), checksum.find() ? checksum.group("hash") : ""
        );
    }

    /**
     * The CycloneDX component this package URL names.
     *
     * @param licenses   the component's license choices
     * @param properties the component's properties
     * @return the component, referenced by this package URL
     */
    Map<String, Object> component(List<Map<String, Object>> licenses, List<Map<String, Object>> properties) {
        Map<String, Object> component = new LinkedHashMap<>();
        component.put("type", "library");
        component.put("bom-ref", this.text);
        Stream.of(Map.entry("group", this.namespace), Map.entry("version", this.version))
            .filter(entry -> !entry.getValue().isEmpty())
            .forEach(entry -> component.put(entry.getKey(), entry.getValue()));
        component.put("name", this.name);
        component.put("purl", this.text);
        if (!this.sha256.isEmpty()) {
            component.put("hashes", List.of(Map.of("alg", "SHA-256", "content", this.sha256)));
        }
        component.put("licenses", licenses);
        component.put("properties", properties);
        return Map.copyOf(component);
    }

    private static String decoded(String part) {
        return URLDecoder.decode(part, StandardCharsets.UTF_8);
    }
}
