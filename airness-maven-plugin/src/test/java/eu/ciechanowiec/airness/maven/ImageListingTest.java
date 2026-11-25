package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * An image is named by the Dockerfile that built it and the distribution it is built on, and its document names the
 * tool that listed it in either shape CycloneDX has used.
 */
class ImageListingTest {

    private static final String DOCKERFILE = "Dockerfile";

    @Test
    void namesAnImageByItsDockerfileAndItsDistribution() {
        ImageInventory alpine = ImageInventory.of(ImageDocuments.document("alpine", "3.24.4"));
        assertEquals("Dockerfile, on Alpine 3.24.4", ImageReportInputs.image(".", DOCKERFILE, alpine));
        assertEquals("web/Dockerfile, on Alpine 3.24.4", ImageReportInputs.image("web", DOCKERFILE, alpine));
        ImageInventory bare = ImageInventory.of(ReportJson.parse("{\"components\": []}"));
        assertEquals("web/Dockerfile", ImageReportInputs.image("web", DOCKERFILE, bare));
    }

    @Test
    void readsTheToolThatListedAnImageInEitherDocumentShape() {
        assertEquals(
            Optional.of("Trivy 0.60.0"), ImageReportInputs.lister(
                ReportJson.parse(
                    "{\"metadata\": {\"tools\": {\"components\": [{\"name\": \"\"},"
                        + " {\"type\": \"application\", \"name\": \"trivy\", \"version\": \"0.60.0\"}]}}}"
                )
            )
        );
        assertEquals(
            Optional.of("Syft"),
            ImageReportInputs.lister(ReportJson.parse("{\"metadata\": {\"tools\": [{\"name\": \"syft\"}]}}"))
        );
        assertEquals(Optional.empty(), ImageReportInputs.lister(ReportJson.parse("{\"metadata\": {}}")));
    }
}
