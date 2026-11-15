package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * The packages of one shipped image as a document the aggregate SBOM merges, beside the npm documents.
 *
 * <p>Trivy's own document is not merged as it is: it is written in a later CycloneDX version than the aggregate one,
 * names its components by references that change on every run, and records the layers of a build that is gone. So
 * the document is rewritten with references that stay the same for the same image: the image is a container
 * component named by its module and Dockerfile, its distribution is an operating-system component, and each package
 * is named by its package URL. The module depends on the image and the image on everything it holds. The licenses an
 * image's metadata declares are kept as written, as names. The license policy reads the Maven and npm packages alone.
 */
@UtilityClass
final class ImageSbom {

    private static final String TYPE = "type";
    private static final String NAME = "name";
    private static final String VERSION = "version";

    /**
     * The document of one image.
     *
     * @param module    the module whose node project holds the Dockerfile
     * @param evidence  the image the Dockerfile built
     * @param inventory what the image holds
     * @return the document, in the shape of an npm document
     */
    static JsonNode document(String module, ImageEvidence evidence, ImageInventory inventory) {
        String root = module + " image " + evidence.dockerfile();
        Stream<Map<String, Object>> system = inventory.distribution().isEmpty() ? Stream.empty() : Stream.of(
            Map.of(
                TYPE, "operating-system", ReportSbom.REFERENCE, "os:" + inventory.distribution() + '@' + inventory
                    .release(), NAME,
                inventory.distribution(), VERSION, inventory.release()
            )
        );
        String use = module + " / " + evidence.dockerfile();
        Map<String, Map<String, Object>> components = new LinkedHashMap<>();
        Stream.concat(system, inventory.packages().stream().map(entry -> component(entry, use)))
            .forEach(
                component -> components.putIfAbsent(String.valueOf(component.get(ReportSbom.REFERENCE)), component)
            );
        Map<String, Object> document = Map.of(
            "metadata", Map.of(
                "component", Map.of(TYPE, "container", ReportSbom.REFERENCE, root, NAME, evidence.dockerfile())
            ),
            "components", List.copyOf(components.values()),
            "dependencies", List.of(
                Map.of("ref", module, "dependsOn", List.of(root)),
                Map.of("ref", root, "dependsOn", List.copyOf(components.keySet()))
            )
        );
        return new ObjectMapper().valueToTree(document);
    }

    private static Map<String, Object> component(ImagePackage entry, String use) {
        Map<String, Object> component = new LinkedHashMap<>();
        component.put(TYPE, "library");
        component.put(ReportSbom.REFERENCE, entry.purl());
        component.put(NAME, entry.name());
        component.put(VERSION, entry.version());
        component.put("purl", entry.purl());
        if (!entry.licenses().isEmpty()) {
            component.put(
                "licenses", entry.licenses().stream().map(license -> Map.of("license", Map.of(NAME, license))).toList()
            );
        }
        component.put("properties", List.of(Map.of(NAME, "airness:image", "value", use)));
        return Map.copyOf(component);
    }
}
