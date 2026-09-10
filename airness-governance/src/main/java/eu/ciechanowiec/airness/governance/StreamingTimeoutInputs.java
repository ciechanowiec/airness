package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * Production declarations bound to one Maven invocation.
 *
 * @param started      the build start
 * @param applications the eligible production applications
 * @param declarations the production inputs and their provenance
 */
public record StreamingTimeoutInputs(long started, List<String> applications, List<StreamingInput> declarations) {

    /**
     * Canonicalizes immutable manifest entries.
     *
     * @param started      the build start
     * @param applications the application identities
     * @param declarations the production inputs
     */
    public StreamingTimeoutInputs {
        applications = List.copyOf(applications.stream().distinct().sorted().toList());
        declarations = List.copyOf(
            declarations.stream().distinct().sorted(Comparator.comparing(StreamingInput::encoded)).toList()
        );
    }

    /**
     * Resolves the fixed manifest alongside context evidence.
     *
     * @param evidence the context evidence
     * @return the input manifest path
     */
    public static Path beside(Path evidence) {
        return evidence.resolveSibling("streaming-timeout-inputs.evidence");
    }

    /**
     * Fingerprints every relevant production declaration.
     *
     * @return the content digest
     */
    public String digest() {
        return fingerprint(String.join("\n", this.inventory()));
    }

    /**
     * Hashes a production input without copying its values into evidence.
     *
     * @param content the source content
     * @return the SHA-256 value
     */
    public static String fingerprint(String content) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("The JVM provides no SHA-256 digest", exception);
        }
    }

    /**
     * Writes only Maven build output.
     *
     * @param destination the fixed manifest path
     */
    public void write(Path destination) {
        try {
            Files.createDirectories(destination.toAbsolutePath().getParent());
            Files.writeString(destination, this.content());
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write streaming timeout inputs", exception);
        }
    }

    /**
     * Rejects a stale or altered production inventory.
     *
     * @param path the prepared manifest
     * @return whether it describes exactly these inputs and this build
     */
    public boolean matches(Path path) {
        try {
            return Files.isRegularFile(path) && Files.readString(path).equals(this.content());
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read streaming timeout inputs", exception);
        }
    }

    private List<String> inventory() {
        return Stream.concat(
            this.applications.stream().map(name -> "application " + name),
            this.declarations.stream().map(input -> "input " + input.encoded())
        ).toList();
    }

    private String content() {
        return "started " + this.started + "\ndigest " + this.digest() + "\n"
            + String.join("\n", this.inventory()) + "\n";
    }
}
