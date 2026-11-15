package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;

/**
 * Lists what a registry image holds, and finds what a tag of it names today, through the host's Docker client.
 *
 * <p>The packages are listed the way the {@code image} gate lists a built image's: the image is saved to a file and
 * handed to the pinned scanner on its standard input, in a container with no network, no capability and no mount,
 * which writes Trivy's CycloneDX document and judges nothing.
 *
 * @param scanner the scanner image the installed harness pins
 * @param docker  the host's Docker client
 */
record DockerImages(String scanner, HostDocker docker) {

    private static final String DOCKER = "docker";
    private static final String DIGEST_SEPARATOR = "@";
    // What runs inside the scanner image, as the image gate runs it: no vulnerability database is read, and Java
    // archives are left to the dependency scan.
    private static final String TRIVY_SCRIPT = "cat > /tmp/image.tar && exec trivy image --quiet --skip-version-check"
        + " --disable-telemetry --input /tmp/image.tar --format cyclonedx --skip-files '**/*.jar'"
        + " --skip-files '**/*.war' --skip-files '**/*.ear'";

    /**
     * The installed harness's scanner on the host's Docker client.
     *
     * @return the images
     */
    static DockerImages system() {
        return new DockerImages(ScannerTools.image("trivy"), HostDocker.system());
    }

    /**
     * Lists the packages an image holds. An image this machine already holds is read as it is, since that is the one a
     * build here used. Any other is pulled first.
     *
     * @param reference the image
     * @return Trivy's CycloneDX document of it
     * @throws IllegalStateException when the image cannot be pulled, saved or listed
     */
    @SneakyThrows
    JsonNode packages(String reference) {
        List<String> inspect = List.of(DOCKER, "image", "inspect", reference);
        if (!this.docker.succeeds(inspect)) {
            this.docker.run(List.of(DOCKER, "pull", "--quiet", reference), Optional.empty());
        }
        Path directory = Files.createTempDirectory("airness-image-");
        Path archive = directory.resolve("image.tar");
        try {
            this.docker.run(List.of(DOCKER, "save", "--output", archive.toString(), reference), Optional.empty());
            String listed = this.docker.run(
                List.of(
                    DOCKER, "run", "--rm", "--interactive", "--cap-drop=ALL", "--security-opt=no-new-privileges",
                    "--network", "none", "--entrypoint", "sh", this.scanner, "-c", TRIVY_SCRIPT
                ), Optional.of(archive)
            );
            JsonNode document = ReportJson.parse(listed);
            if (!NpmEvidence.CYCLONEDX.equals(document.path(NpmEvidence.FORMAT).asText())) {
                throw new IllegalStateException("The scanner listed no CycloneDX document for " + reference);
            }
            return document;
        } finally {
            delete(archive, directory);
        }
    }

    /**
     * The image a tag names in its registry today, pinned by the digest it resolves to.
     *
     * @param tagged the repository and tag, without a digest, such as {@code node:26.8.2-alpine}
     * @return the tag with the digest it names now, such as {@code node:26.8.2-alpine@sha256:...}
     * @throws IllegalStateException when the tag cannot be pulled or names no digest
     */
    String current(String tagged) {
        this.docker.run(List.of(DOCKER, "pull", "--quiet", tagged), Optional.empty());
        JsonNode digests = ReportJson.parse(
            this.docker.run(
                List.of(DOCKER, "image", "inspect", "--format", "{{json .RepoDigests}}", tagged), Optional.empty()
            )
        );
        return digests.valueStream().map(JsonNode::asText).filter(entry -> entry.contains(DIGEST_SEPARATOR))
            .map(entry -> tagged + entry.substring(entry.indexOf(DIGEST_SEPARATOR))).findFirst()
            .orElseThrow(() -> new IllegalStateException("Docker records no registry digest for " + tagged));
    }

    private static void delete(Path archive, Path directory) {
        try {
            Files.deleteIfExists(archive);
            Files.deleteIfExists(directory);
        } catch (IOException exception) {
            throw new UncheckedIOException("The saved image could not be removed: " + archive, exception);
        }
    }
}
