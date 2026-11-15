package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * Trivy's CycloneDX documents of images, written as Trivy writes them: an operating-system component, and packages
 * named by package URLs that carry their architecture, with their source packages as Trivy properties.
 */
@UtilityClass
final class ImageDocuments {

    /**
     * An Alpine system package built from a source package of the same version.
     *
     * @param name    the binary package
     * @param source  the source package
     * @param version the version
     * @return the component
     */
    static String apk(String name, String source, String version) {
        return """
            {"type": "library", "name": "%1$s", "version": "%3$s",
             "purl": "pkg:apk/alpine/%1$s@%3$s?arch=aarch64&distro=3.24.1",
             "licenses": [{"license": {"id": "Zlib"}}],
             "properties": [{"name": "aquasecurity:trivy:SrcName", "value": "%2$s"},
                            {"name": "aquasecurity:trivy:SrcVersion", "value": "%3$s"},
                            {"name": "aquasecurity:trivy:LayerDiffID", "value": "sha256:layer"}]}
            """.formatted(name, source, version);
    }

    /**
     * An npm package.
     *
     * @param name    the package
     * @param version the version
     * @return the component
     */
    static String npm(String name, String version) {
        return """
            {"type": "library", "name": "%1$s", "version": "%2$s", "purl": "pkg:npm/%1$s@%2$s",
             "licenses": [{"license": {"name": "MIT"}}]}
            """.formatted(name, version);
    }

    /**
     * A document of an image built on a distribution.
     *
     * @param distribution the distribution, such as {@code alpine}
     * @param release      its release, such as {@code 3.24.1}
     * @param components   the packages
     * @return the document
     */
    static JsonNode document(String distribution, String release, String... components) {
        String system = """
            {"bom-ref": "6f1c635c", "type": "operating-system", "name": "%s", "version": "%s",
             "properties": [{"name": "aquasecurity:trivy:Class", "value": "os-pkgs"}]}
            """.formatted(distribution, release);
        String listed = Arrays.stream(components).map(component -> ", " + component).collect(Collectors.joining());
        return ReportJson.parse(
            "{\"bomFormat\": \"CycloneDX\", \"specVersion\": \"1.7\", \"metadata\": {\"component\": {\"bom-ref\":"
                + " \"0f9e\", \"type\": \"container\", \"name\": \"image.tar\"}}, \"components\": [" + system + listed
                + "]}"
        );
    }
}
