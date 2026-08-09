package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArtifactContentCheckTest {

    private static final String DEVELOPMENT = "Source or development files packaged in the JAR";
    private static final String LOCAL = "Machine-local repository paths packaged in the JAR";
    private static final String MAIN = "main";
    private static final String SECRETS = "Recognizable secret material packaged in the JAR";
    private static final String TEST = "test";
    private static final String TESTS = "Test-only output packaged in the JAR";
    private static final String UNSAFE = "Duplicate or unsafe JAR entries";
    private static final String VALUE = "value";

    @TempDir
    private Path directory;

    @Test
    void acceptsProductionOutputAndOrdinaryMetadata() {
        Path main = this.output(MAIN, "com/example/Example.class");
        Path jar = this.jar(
            Map.of(
                "com/example/Example.class", "bytecode",
                "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"
            )
        );
        assertTrue(this.check(jar, main, this.directory.resolve(TEST)).stream().allMatch(Findings::clean));
    }

    @Test
    void rejectsUnsafeAndDevelopmentEntries() {
        Path jar = this.jar(Map.of("../outside.txt", VALUE, ".idea/workspace.xml", VALUE));
        List<Findings> findings = this.check(jar, this.directory.resolve(MAIN), this.directory.resolve(TEST));
        assertEquals(List.of("../outside.txt"), offences(findings, UNSAFE));
        assertEquals(List.of(".idea/workspace.xml"), offences(findings, DEVELOPMENT));
    }

    @Test
    void recognizesEveryUnsafePathShapeAndDevelopmentFileShape() {
        Path jar = this.jar(
            Map.of(
                "/absolute.txt", VALUE,
                "\\absolute.txt", VALUE,
                "C:drive.txt", VALUE,
                "folder\\child.txt", VALUE,
                "folder\\..\\outside.txt", VALUE,
                "Source.java", VALUE,
                "module.iml", VALUE,
                ".classpath", VALUE
            )
        );
        List<Findings> findings = this.check(jar, this.directory.resolve(MAIN), this.directory.resolve(TEST));
        assertEquals(5, offences(findings, UNSAFE).size());
        assertEquals(3, offences(findings, DEVELOPMENT).size());
    }

    @Test
    void rejectsOutputThatExistsOnlyInTheTestDirectory() {
        Path test = this.output(TEST, "com/example/FixtureTest.class");
        Path jar = this.jar(Map.of("com/example/FixtureTest.class", "bytecode"));
        assertEquals(
            List.of("com/example/FixtureTest.class"),
            offences(this.check(jar, this.directory.resolve(MAIN), test), TESTS)
        );
    }

    @Test
    void rejectsTheMachinePathInsidePackagedBytes() {
        Path jar = this.jar(Map.of("build.properties", this.directory.toString()));
        assertEquals(
            List.of("build.properties"),
            offences(this.check(jar, this.directory.resolve(MAIN), this.directory.resolve(TEST)), LOCAL)
        );
    }

    @Test
    void rejectsRecognizablePrivateKeyMaterial() {
        String key = "-----BEGIN PRIVATE KEY-----\nfixture\n-----END PRIVATE KEY-----";
        Path jar = this.jar(Map.of("credentials.txt", key));
        assertEquals(
            List.of("credentials.txt"),
            offences(this.check(jar, this.directory.resolve(MAIN), this.directory.resolve(TEST)), SECRETS)
        );
    }

    @Test
    void recognizesRepositoryAndGitHubCredentialShapes() {
        Path jar = this.jar(
            Map.of(
                "aws.txt", "AKIA1234567890123456",
                "github.txt", "ghp_12345678901234567890"
            )
        );
        assertEquals(
            2,
            offences(this.check(jar, this.directory.resolve(MAIN), this.directory.resolve(TEST)), SECRETS)
                .size()
        );
    }

    @Test
    void doesNotTreatProductionOutputAsTestOnlyWhenBothDirectoriesContainIt() {
        Path main = this.output("main-shared", "com/example/Shared.class");
        Path test = this.output("test-shared", "com/example/Shared.class");
        Path jar = this.jar(Map.of("com/example/Shared.class", "bytecode"));
        assertTrue(offences(this.check(jar, main, test), TESTS).isEmpty());
    }

    @Test
    void reportsAnUnreadableArchive() {
        Path archive = this.output("broken", "artifact.jar").resolve("artifact.jar");
        assertThrows(
            UncheckedIOException.class,
            () -> this.check(archive, this.directory.resolve(MAIN), this.directory.resolve(TEST))
        );
    }

    private List<Findings> check(Path jar, Path main, Path test) {
        return new ArtifactContentCheck(jar, main, test, this.directory).findings();
    }

    @SneakyThrows
    private Path output(String name, String entry) {
        Path file = this.directory.resolve(name).resolve(entry);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "bytecode");
        return this.directory.resolve(name);
    }

    @SneakyThrows
    private Path jar(Map<String, String> entries) {
        Path jar = this.directory.resolve("artifact-" + this.entryCount() + ".jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            entries.forEach((name, content) -> writeEntry(output, name, content));
        }
        return jar;
    }

    @SneakyThrows
    private static void writeEntry(JarOutputStream output, String name, String content) {
        output.putNextEntry(new JarEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    @SneakyThrows
    private long entryCount() {
        try (Stream<Path> paths = Files.list(this.directory)) {
            return paths.count();
        }
    }

    private static List<String> offences(Collection<Findings> findings, String headline) {
        return findings.stream()
            .filter(finding -> headline.equals(finding.headline()))
            .findFirst()
            .orElseThrow()
            .offences();
    }
}
