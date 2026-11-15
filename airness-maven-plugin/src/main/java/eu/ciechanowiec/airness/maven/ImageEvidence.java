package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The packages one image held, as the {@code image} gate listed them beside its result before removing the image.
 *
 * <p>The gate names each file it wrote in its result's evidence, and those are the files read here: a file an
 * earlier run left behind is cleared before the gate runs, so it cannot stand in for an image this run built. A
 * listed file that is missing or malformed fails the check that reads it, never an empty image that passes.
 *
 * @param dockerfile the Dockerfile that built the image, relative to the node project's directory
 * @param file       the Dockerfile itself
 * @param sbom       Trivy's CycloneDX document of what the image holds
 */
record ImageEvidence(String dockerfile, Path file, JsonNode sbom) {

    /**
     * The result file the {@code image} gate writes beside its evidence.
     */
    static final String RESULT = "image.json";

    private static final int CONTRACT = 1;
    private static final String PREFIX = "image-";

    /**
     * The images the {@code image} gate listed for one node project.
     *
     * @param project the node project
     * @return the images, or empty when the gate wrote no result, as a build that never reached it
     * @throws IllegalStateException when a listed file is missing or is not the contract this reads
     */
    static Optional<List<ImageEvidence>> of(NpmEvidence project) {
        Path result = project.results().resolve(RESULT);
        if (!Files.isRegularFile(result)) {
            return Optional.empty();
        }
        return Optional.of(
            ReportJson.read(result).path("evidence").valueStream().map(JsonNode::asText)
                .filter(name -> name.startsWith(PREFIX)).map(name -> read(project, name)).toList()
        );
    }

    private static ImageEvidence read(NpmEvidence project, String name) {
        Path path = project.results().resolve(name);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException(
                project.subject() + ": the image gate lists " + name + ", which is not at " + path
            );
        }
        JsonNode document = ReportJson.read(path);
        JsonNode sbom = document.path("sbom");
        String dockerfile = document.path("dockerfile").asText();
        if (
            document.path(NpmEvidence.CONTRACT_FIELD).asInt() != CONTRACT || dockerfile.isEmpty()
                || !NpmEvidence.CYCLONEDX.equals(sbom.path(NpmEvidence.FORMAT).asText())
        ) {
            throw new IllegalStateException(
                project.subject() + ": " + name + " is not the contract " + CONTRACT + " list of an image's packages,"
                    + " which names its Dockerfile and holds a CycloneDX document"
            );
        }
        return new ImageEvidence(dockerfile, project.directory().resolve(dockerfile).normalize(), sbom);
    }
}
