package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One resolved npm package as the inventory lists it.
 *
 * @param name       the package name, scoped or not
 * @param version    the resolved version
 * @param license    the declared license expression, empty when none is declared or it cannot be read here
 * @param direct     whether a member of the project depends on it directly
 * @param installed  whether this host installed it, which a package limited to other platforms is not
 * @param production whether a member reaches it through its dependencies or optional dependencies, so the
 *                   product ships it
 * @param notices    the license, notice and copying files a shipped package carries, by file name
 */
record NpmPackage(
    String name, String version, String license, boolean direct, boolean installed, boolean production,
    Map<String, String> notices
) {

    private static final String NAME = "name";
    private static final String VERSION = "version";
    private static final String LICENSE = "license";
    private static final String DIRECT = "direct";
    private static final String INSTALLED = "installed";
    private static final String PRODUCTION = "production";
    private static final String NOTICES = "notices";

    NpmPackage {
        notices = Map.copyOf(notices);
    }

    static NpmPackage of(JsonNode entry) {
        Map<String, String> notices = entry.path(NOTICES).properties().stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, notice -> notice.getValue().asText()));
        return new NpmPackage(
            entry.path(NAME).asText(), entry.path(VERSION).asText(), entry.path(LICENSE).asText(),
            entry.path(DIRECT).asBoolean(), entry.path(INSTALLED).asBoolean(), entry.path(PRODUCTION).asBoolean(),
            notices
        );
    }

    /**
     * The package URL, with a scope written as its encoded namespace, as the npm SBOM writes it.
     *
     * @return the package URL
     */
    String purl() {
        String encoded = this.name.startsWith("@") ? "%40" + this.name.substring(1) : this.name;
        return this.version.isEmpty() ? "pkg:npm/" + encoded : "pkg:npm/" + encoded + '@' + this.version;
    }
}
