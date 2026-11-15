package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The images the image gate listed are read from the files its result names, and a named file that is missing or
 * malformed fails rather than standing for an image that holds nothing.
 */
class ImageEvidenceTest {

    private static final String LISTED = """
        {"contract": 1, "dockerfile": "web/Dockerfile", "sbom": {"bomFormat": "CycloneDX", "components": []}}
        """;

    @TempDir
    private Path directory;

    @Test
    void readsNothingWhereTheImageGateWroteNoResult() {
        assertEquals(Optional.empty(), ImageEvidence.of(this.node()));
    }

    @Test
    void readsEveryImageTheResultNamesAndNothingElseBesideIt() {
        NpmEvidence node = this.node();
        this.write(node, "image-web-Dockerfile.json", LISTED);
        this.write(node, "image-stale.json", LISTED);
        this.write(node, ImageEvidence.RESULT, "{\"evidence\": [\"image-web-Dockerfile.json\", \"notes.json\"]}");
        List<ImageEvidence> images = ImageEvidence.of(node).orElseThrow();
        assertAll(
            () -> assertEquals(1, images.size(), "a file the result does not name is left over from another run"),
            () -> assertEquals("web/Dockerfile", images.getFirst().dockerfile()),
            () -> assertEquals(node.directory().resolve("web/Dockerfile"), images.getFirst().file()),
            () -> assertEquals("CycloneDX", images.getFirst().sbom().path("bomFormat").asText())
        );
    }

    @Test
    void failsOnANamedImageThatIsMissingOrNotTheContractItReads() {
        NpmEvidence missing = this.node();
        this.write(missing, ImageEvidence.RESULT, "{\"evidence\": [\"image-Dockerfile.json\"]}");
        IllegalStateException absent = assertThrows(IllegalStateException.class, () -> ImageEvidence.of(missing));
        this.write(missing, "image-Dockerfile.json", "{\"contract\": 2, \"dockerfile\": \"Dockerfile\", \"sbom\": {}}");
        IllegalStateException malformed = assertThrows(IllegalStateException.class, () -> ImageEvidence.of(missing));
        assertAll(
            () -> assertTrue(
                Objects.requireNonNull(absent.getMessage()).contains("the image gate lists image-Dockerfile.json"),
                absent.getMessage()
            ),
            () -> assertTrue(
                Objects.requireNonNull(malformed.getMessage()).contains("is not the contract 1 list"), malformed
                    .getMessage()
            )
        );
    }

    @SneakyThrows
    private NpmEvidence node() {
        Path results = Files.createDirectories(this.directory.resolve("target/airness/node/projects/web"));
        return new NpmEvidence("com.example:shop:pom:1", "web", this.directory.resolve("web"), results);
    }

    @SneakyThrows
    private void write(NpmEvidence node, String name, String content) {
        Files.writeString(node.results().resolve(name), content);
    }
}
