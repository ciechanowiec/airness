package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.ciechanowiec.airness.governance.ReportPackage;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.SneakyThrows;

/**
 * An exact artifact whose licensing evidence has been verified by Airness.
 *
 * @param coordinate complete Maven coordinate, including type and any classifier
 * @param sha256     checksum of the verified artifact bytes
 * @param license    canonical license identifier, still subject to the inherited allowlist
 * @param evidence   expected metadata and retained upstream evidence
 */
record LicenseMetadata(String coordinate, String sha256, String license, LicenseEvidence evidence) {

    private static final String ROOT = "/eu/ciechanowiec/airness/licenses/";

    @SneakyThrows
    static List<LicenseMetadata> entries() {
        return List.of(new ObjectMapper().readValue(resource("metadata.json"), LicenseMetadata[].class));
    }

    static Optional<LicenseMetadata> find(String coordinate) {
        return entries().stream().filter(entry -> entry.coordinate.equals(coordinate)).findFirst();
    }

    void verify(Path artifact, List<String> declared) {
        if (!this.sha256.equals(this.hash(artifact))) {
            throw new IllegalStateException("License evidence checksum mismatch: " + this.coordinate);
        }
        if (!this.evidence.declared().equals(declared)) {
            throw new IllegalStateException("License evidence conflicts with supplier declaration: " + this.coordinate);
        }
    }

    @SneakyThrows
    private String hash(Path artifact) {
        if (!Files.isRegularFile(artifact) || Files.isSymbolicLink(artifact)) {
            throw new IllegalStateException("Unreadable artifact for license evidence: " + this.coordinate);
        }
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(artifact)));
    }

    Map<String, String> notices() {
        return Map.of(
            ReportPackage.LICENSE_EVIDENCE_PREFIX + this.evidence.source(),
            "Verified license: " + this.license + "\nArtifact SHA-256: " + this.sha256 + "\n"
                + this.evidence.description()
        );
    }

    @SneakyThrows
    static String resource(String name) {
        URL resource = Optional.ofNullable(LicenseMetadata.class.getResource(ROOT + name))
            .orElseThrow(() -> new IllegalStateException("Missing bundled license evidence: " + name));
        try (InputStream stream = resource.openStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
