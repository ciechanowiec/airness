package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.maven.project.MavenProject;

/**
 * The npm dependency evidence one declared node project left in its module's build directory.
 *
 * <p>{@code node-gate-npm-inventory} writes {@code dependencies.json} at {@code prepare-package} in every
 * build that judges anything, and under the extended profile {@code node-gate-npm-sbom} writes
 * {@code bom.cdx.json} beside it. The shared dependency policies read them from here. A missing or unreadable
 * file is a failure of the policy that needed it, never an empty inventory that passes.
 *
 * @param module    the id of the module that declares the node project
 * @param key       the node project's key, {@code .} for the module itself
 * @param directory the node project's directory
 * @param results   the node project's result directory
 */
record NpmEvidence(String module, String key, Path directory, Path results) {

    static final String INVENTORY = "dependencies.json";
    static final String SBOM = "bom.cdx.json";
    private static final String WIRING = "wiring.json";
    private static final String CYCLONEDX = "CycloneDX";
    private static final String FORMAT = "bomFormat";
    private static final String PACKAGES = "packages";
    private static final int CONTRACT = 1;
    private static final String CONTRACT_FIELD = "contract";
    private static final String PROPERTY = "airness.node.projects";
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * The evidence of every node project a module declares. A harness parent declares none of its own, and a
     * listed directory that is no node project inside the module has none: node-projects reports it.
     *
     * @param project the module
     * @return the evidence locations, in declaration order
     */
    static List<NpmEvidence> of(MavenProject project) {
        if (RepositoryProjects.harnessParent(project)) {
            return List.of();
        }
        Path build = Path.of(project.getBuild().getDirectory());
        return NodeProjects.of(project.getBasedir().toPath(), project.getProperties().getProperty(PROPERTY))
            .projects().stream()
            .filter(node -> !node.outside() && node.hasManifest())
            .map(node -> new NpmEvidence(project.getId(), node.key(), node.directory(), node.results(build)))
            .toList();
    }

    /**
     * The directories of every node project a module declares, which the suppression budget reads.
     *
     * @param project the module
     * @return the node project directories
     */
    static List<Path> directories(MavenProject project) {
        return of(project).stream().map(NpmEvidence::directory).toList();
    }

    /**
     * The packages {@code dependencies.json} lists.
     *
     * @return the packages
     * @throws IllegalStateException when the inventory is missing or malformed
     */
    List<NpmPackage> packages() {
        JsonNode inventory = this.read(INVENTORY);
        JsonNode packages = inventory.path(PACKAGES);
        if (
            inventory.path(CONTRACT_FIELD).asInt() != CONTRACT || !packages.isArray()
                || !packages.valueStream().allMatch(NpmPackage::complete)
        ) {
            throw new IllegalStateException(
                this.subject() + ": " + INVENTORY + " is not the contract " + CONTRACT + " inventory, whose every"
                    + " package names a name, a version, a license, and whether it is direct and installed"
            );
        }
        return packages.valueStream().map(NpmPackage::of).toList();
    }

    /**
     * The CycloneDX document {@code node-gate-npm-sbom} wrote.
     *
     * @return the parsed document
     * @throws IllegalStateException when the document is missing or malformed
     */
    JsonNode sbom() {
        JsonNode document = this.read(SBOM);
        if (!CYCLONEDX.equals(document.path(FORMAT).asText())) {
            throw new IllegalStateException(this.subject() + ": " + SBOM + " is no CycloneDX document");
        }
        return document;
    }

    /**
     * Whether the {@code wiring} gate failed for this project, which only a report-only build gets past. The
     * inventory written after it then describes packages the harness does not vouch for.
     *
     * @return whether the wiring result says failed
     */
    boolean wiringFailed() {
        Path result = this.results.resolve(WIRING);
        return Files.isRegularFile(result) && Optional.of(this.read(WIRING))
            .map(document -> "failed".equals(document.path("status").asText()))
            .orElse(false);
    }

    /**
     * Names the node project for a reader: the module and the key.
     *
     * @return the subject, such as {@code com.example:web:pom:1.0 node project .}
     */
    String subject() {
        return this.module + " node project " + this.key;
    }

    private JsonNode read(String file) {
        Path path = this.results.resolve(file);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException(
                this.subject() + " has no " + file + " at " + path + ", so the policy that reads it fails rather than"
                    + " passing over an empty inventory"
            );
        }
        try {
            return JSON.readTree(path.toFile());
        } catch (IOException exception) {
            throw new IllegalStateException(this.subject() + ": " + file + " is unreadable", exception);
        }
    }

    /**
     * One resolved npm package as the inventory lists it.
     *
     * @param name      the package name, scoped or not
     * @param version   the resolved version
     * @param license   the declared license expression, empty when none is declared or it cannot be read here
     * @param direct    whether a member of the project depends on it directly
     * @param installed whether this host installed it, which a package limited to other platforms is not
     */
    record NpmPackage(String name, String version, String license, boolean direct, boolean installed) {

        private static final String NAME = "name";
        private static final String VERSION = "version";
        private static final String LICENSE = "license";
        private static final String DIRECT = "direct";
        private static final String INSTALLED = "installed";

        static NpmPackage of(JsonNode entry) {
            return new NpmPackage(
                entry.path(NAME).asText(), entry.path(VERSION).asText(), entry.path(LICENSE).asText(),
                entry.path(DIRECT).asBoolean(), entry.path(INSTALLED).asBoolean()
            );
        }

        // A field the inventory leaves out is refused rather than defaulted, so a drifted producer cannot make the
        // policies read a package as direct or installed when it said nothing.
        static boolean complete(JsonNode entry) {
            boolean named = Stream.of(NAME, VERSION, LICENSE).allMatch(field -> entry.path(field).isTextual());
            return named && Stream.of(DIRECT, INSTALLED).allMatch(field -> entry.path(field).isBoolean());
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
}
