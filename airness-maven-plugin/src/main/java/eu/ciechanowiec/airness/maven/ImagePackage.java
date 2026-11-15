package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One package an image holds, read from a component of Trivy's CycloneDX document.
 *
 * <p>A system package, which a distribution's package manager installed, is judged by its source package: the
 * distribution files its advisories there, and versions it the way its own manager does, with the epoch and the
 * revision a Debian version carries. A language package, such as the npm a Node.js image ships, is judged by its
 * package URL. The URL keeps no {@code arch} qualifier, so the same image reads the same on every machine.
 *
 * @param purl          the package URL, without its architecture
 * @param name          the package name
 * @param version       the package version
 * @param system        whether a distribution's package manager installed it
 * @param source        the source package of a system package, the package name otherwise
 * @param sourceVersion the source package's version as the distribution writes it, the package version otherwise
 * @param licenses      the licenses the image's metadata declares, as written
 */
record ImagePackage(
    String purl, String name, String version, boolean system, String source, String sourceVersion,
    List<String> licenses
) {

    private static final String PROPERTY = "aquasecurity:trivy:";
    private static final List<String> SYSTEM_TYPES = List.of("pkg:apk/", "pkg:deb/", "pkg:rpm/");
    private static final Pattern ARCHITECTURE = Pattern.compile("(?<=[?&])arch=[^&]*&?");
    private static final Pattern DANGLING = Pattern.compile("[?&]$");

    ImagePackage {
        licenses = List.copyOf(licenses);
    }

    /**
     * Reads one component of an image's document.
     *
     * @param component the component
     * @return the package, or empty for a component without a package URL, such as the operating system itself
     */
    static Optional<ImagePackage> of(JsonNode component) {
        String raw = component.path("purl").asText();
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        String purl = DANGLING.matcher(ARCHITECTURE.matcher(raw).replaceAll("")).replaceAll("");
        String name = component.path("name").asText();
        String version = component.path("version").asText();
        boolean system = SYSTEM_TYPES.stream().anyMatch(raw::startsWith);
        String source = property(component, "SrcName").orElse(name);
        String sourceVersion = system ? sourceVersion(component, version) : version;
        List<String> licenses = component.path("licenses").valueStream()
            .map(entry -> entry.path("license"))
            .map(license -> license.path("id").asText(license.path("name").asText()))
            .filter(license -> !license.isBlank()).toList();
        return Optional.of(new ImagePackage(purl, name, version, system, source, sourceVersion, licenses));
    }

    /**
     * What identifies the package across two releases of one image, whatever version each holds: the source
     * package of a system package, and the package URL without its version for any other.
     *
     * @return the identity
     */
    String identity() {
        if (this.system) {
            return "system:" + this.source;
        }
        int at = this.purl.lastIndexOf('@');
        return at < 0 ? this.purl : this.purl.substring(0, at);
    }

    // The source package's version, as the distribution writes it: [epoch:]version[-revision].
    private static String sourceVersion(JsonNode component, String version) {
        Optional<String> upstream = property(component, "SrcVersion");
        if (upstream.isEmpty()) {
            return version;
        }
        String epoch = property(component, "SrcEpoch").filter(value -> !"0".equals(value)).map(value -> value + ':')
            .orElse("");
        String release = property(component, "SrcRelease").map(value -> '-' + value).orElse("");
        return epoch + upstream.orElseThrow() + release;
    }

    private static Optional<String> property(JsonNode component, String name) {
        return component.path("properties").valueStream()
            .filter(entry -> (PROPERTY + name).equals(entry.path("name").asText()))
            .map(entry -> entry.path("value").asText()).filter(value -> !value.isEmpty()).findFirst();
    }
}
