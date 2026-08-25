package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublicationContentCheckTest {

    private static final String LOCAL = "Machine-local paths in Maven publication files";
    private static final String MISSING = "Missing Maven publication files";
    private static final String SECRETS = "Recognizable secrets in Maven publication files";

    @TempDir
    private Path directory;

    @Test
    @SneakyThrows
    void acceptsCompleteCleanPublication() {
        Path pom = Files.writeString(this.directory.resolve("artifact.pom"), "<project/>");
        Path jar = this.jar("content.txt", "ordinary bytes");
        assertTrue(
            new PublicationContentCheck(List.of(pom, jar), this.directory)
                .findings().stream().allMatch(Findings::clean)
        );
    }

    @Test
    @SneakyThrows
    void reportsMissingPublicationFile() {
        List<Findings> findings = new PublicationContentCheck(
            List.of(this.directory.resolve("missing.jar")), this.directory
        ).findings();
        assertEquals(1, offences(findings, MISSING).size());
    }

    @Test
    @SneakyThrows
    void rejectsLocalPathsAndSecretsAcrossFormats() {
        Path pom = Files.writeString(this.directory.resolve("artifact.pom"), this.directory.toString());
        Path jar = this.jar("secret.txt", String.join("", "AKIA", "1234567890123456"));
        List<Findings> findings = new PublicationContentCheck(List.of(pom, jar), this.directory).findings();
        assertEquals(List.of(pom.toString()), offences(findings, LOCAL));
        assertEquals(List.of(jar + "!secret.txt"), offences(findings, SECRETS));
    }

    @Test
    @SneakyThrows
    void findsALocalPathInAPomWhenTheRootCarriesNonAsciiCharacters() {
        Path root = Files.createDirectory(this.directory.resolve("prosjektmappe-æøå"));
        Path pom = Files.writeString(root.resolve("artifact.pom"), root.toString());
        assertEquals(
            List.of(pom.toString()),
            offences(new PublicationContentCheck(List.of(pom), root).findings(), LOCAL),
            "the path this searches for is held as bytes, so the file it searches has to be read as bytes too"
        );
    }

    @SneakyThrows
    private Path jar(String name, String content) {
        Path jar = this.directory.resolve("artifact.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(name));
            output.write(content.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return jar;
    }

    private static List<String> offences(Collection<Findings> findings, String headline) {
        return findings.stream().filter(finding -> headline.equals(finding.headline()))
            .findFirst().orElseThrow().offences();
    }
}
