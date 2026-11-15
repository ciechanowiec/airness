package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * An image's packages enter the aggregate SBOM under references that stay the same for the same image, joined to
 * the module, in the CycloneDX version the aggregate document is validated against.
 */
class ImageSbomTest {

    private static final String MODULE = "com.example:shop:pom:1";
    private static final ImageEvidence EVIDENCE = new ImageEvidence(
        "web/Dockerfile", Path.of("web/Dockerfile"), ImageDocuments.document("alpine", "3.24.1")
    );

    @Test
    void namesTheImageItsDistributionAndEachPackageOnce() {
        ImageInventory inventory = ImageInventory.of(
            ImageDocuments.document(
                "alpine", "3.24.1", ImageDocuments.apk("zlib", "zlib", "1.3.2-r0"), ImageDocuments.npm(
                    "glob", "13.0.6"
                ),
                ImageDocuments.npm("glob", "13.0.6")
            )
        );
        JsonNode document = ImageSbom.document(MODULE, EVIDENCE, inventory);
        String root = MODULE + " image web/Dockerfile";
        assertAll(
            () -> assertEquals(root, document.path("metadata").path("component").path("bom-ref").asText()),
            () -> assertEquals(
                List.of("os:alpine@3.24.1", "pkg:apk/alpine/zlib@1.3.2-r0?distro=3.24.1", "pkg:npm/glob@13.0.6"),
                document.path("components").valueStream().map(component -> component.path("bom-ref").asText()).toList()
            ),
            () -> assertEquals(
                "Zlib", document.path("components").get(1).path("licenses").get(0).path("license").path("name").asText()
            ),
            () -> assertEquals(
                MODULE + " / web/Dockerfile", document.path("components").get(1).path("properties").get(0).path("value")
                    .asText()
            ),
            () -> assertEquals(
                List.of(root), document.path("dependencies").get(0).path("dependsOn").valueStream().map(
                    JsonNode::asText
                ).toList()
            )
        );
    }

    @Test
    void entersAnAggregateSbomThatPassesItsSchema() {
        ImageInventory inventory = ImageInventory.of(
            ImageDocuments.document("alpine", "3.24.1", ImageDocuments.apk("zlib", "zlib", "1.3.2-r0"))
        );
        String documents = ReportJson.encode(List.of(ImageSbom.document(MODULE, EVIDENCE, inventory)));
        ReportModule module = new ReportModule(
            MODULE, List.of(), List.of(), Map.of(NpmReportInputs.DOCUMENTS, documents)
        );
        String aggregate = ReportSbom.assemble(List.of(), List.of(module));
        assertAll(
            () -> assertTrue(aggregate.contains("\"type\":\"container\""), aggregate),
            () -> assertTrue(
                aggregate.contains("\"ref\":\"" + MODULE + "\""), "the module depends on the image it ships"
            )
        );
    }

    @Test
    void namesNoDistributionForAnImageWithoutOne() {
        String unlicensed
            = "{\"name\": \"tool\", \"version\": \"1.0.0\", \"purl\": \"pkg:golang/example.com/tool@1.0.0\"}";
        ImageInventory inventory = ImageInventory.of(ImageDocuments.document("", "", unlicensed));
        JsonNode document = ImageSbom.document(MODULE, EVIDENCE, inventory);
        assertAll(
            () -> assertEquals(
                List.of("pkg:golang/example.com/tool@1.0.0"),
                document.path("components").valueStream().map(component -> component.path("bom-ref").asText()).toList()
            ),
            () -> assertTrue(
                document.path("components").get(0).path("licenses").isMissingNode(), "no license is invented"
            )
        );
    }
}
