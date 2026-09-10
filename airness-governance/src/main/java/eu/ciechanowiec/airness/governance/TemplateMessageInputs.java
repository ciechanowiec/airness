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
 * Current-build message references supplied to the existing Spring startup listener.
 *
 * @param started      the Maven session start in milliseconds
 * @param applications the production application classes
 * @param references   the literal template references
 */
public record TemplateMessageInputs(
    long started, List<String> applications, List<TemplateMessageReference> references
) {

    /**
     * Canonicalizes the manifest's sets for stable fingerprints.
     *
     * @param started      the build start
     * @param applications the production applications
     * @param references   the references
     */
    public TemplateMessageInputs {
        applications = List.copyOf(applications.stream().distinct().sorted().toList());
        references = List.copyOf(
            references.stream().distinct()
                .sorted(Comparator.comparing(TemplateMessageReference::encoded)).toList()
        );
    }

    /**
     * Names the fixed manifest next to the existing context evidence.
     *
     * @param evidence the context evidence file
     * @return its message manifest sibling
     */
    public static Path beside(Path evidence) {
        return evidence.resolveSibling("template-message-inputs.evidence");
    }

    /**
     * Identifies the reference inventory independently of the build time.
     *
     * @return its SHA-256 fingerprint
     */
    public String digest() {
        return fingerprint(String.join("\n", this.inventory()));
    }

    /**
     * Writes build output without changing a repository input.
     *
     * @param destination the manifest path
     */
    public void write(Path destination) {
        try {
            Files.createDirectories(destination.toAbsolutePath().getParent());
            Files.writeString(destination, this.content());
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write template message inputs " + destination, exception);
        }
    }

    /**
     * Verifies that the prepared inventory still describes this build and these references.
     *
     * @param path the manifest path
     * @return whether it matches exactly
     */
    public boolean matches(Path path) {
        try {
            return Files.isRegularFile(path) && Files.readString(path).equals(this.content());
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read template message inputs " + path, exception);
        }
    }

    private String content() {
        return String.join(
            "\n", Stream.concat(
                Stream.of("started " + this.started, "digest " + this.digest()),
                this.inventory().stream()
            ).toList()
        ) + '\n';
    }

    private List<String> inventory() {
        return Stream.concat(
            this.applications.stream().map(application -> "application " + application),
            this.references.stream().map(reference -> "reference " + reference.encoded())
        ).toList();
    }

    private static String fingerprint(String text) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("The JVM provides no SHA-256 digest", exception);
        }
    }
}
