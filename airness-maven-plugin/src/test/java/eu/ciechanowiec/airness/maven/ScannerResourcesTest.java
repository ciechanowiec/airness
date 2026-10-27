package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.SuppressionDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScannerResourcesTest {

    @Test
    @SneakyThrows
    void bundlesDatedExceptionsForNamedAdvisories(@TempDir Path directory) {
        Path eclipse = ScannerResources.copy("eclipse-suppressions.xml", directory);
        assertTrue(new SuppressionDocument(eclipse).problems().isEmpty());
        Path tools = ScannerResources.copy("tool-suppressions.xml", directory);
        assertTrue(new SuppressionDocument(tools).problems().isEmpty());
        Path self = ScannerResources.copy("self-tool-suppressions.xml", directory);
        assertTrue(new SuppressionDocument(self).problems().isEmpty());
        Path spring = ScannerResources.copy("spring-tool-suppressions.xml", directory);
        assertTrue(new SuppressionDocument(spring).problems().isEmpty());
    }

    @Test
    @SneakyThrows
    void readsProducerResourcesAndRefusesMissingOnes(@TempDir Path directory) {
        Path policy = ScannerResources.copy("checkov-policy.tsv", directory);
        assertTrue(Files.readString(policy).contains("CKV_DOCKER_2"));
        assertThrows(IOException.class, () -> ScannerResources.copy("not-a-resource", directory));
    }

    @Test
    @SneakyThrows
    void keepsOnlyTheToolSuppressionsAModuleResolves(@TempDir Path directory) {
        String checkstyle = "pkg:maven/org.apache.maven.plugins/maven-checkstyle-plugin@3.6.0?type=jar";
        Path generic = Files.createDirectories(directory.resolve("generic"));
        Path written = ScannerResources.copyApplicable(
            generic, List.of("pkg:maven/eu.ciechanowiec/airness-maven-plugin@1?type=jar")
        );
        String none = Files.readString(written);
        assertFalse(none.contains("<suppress>"), "a module on airness-parent resolves neither wrapper: " + none);
        assertTrue(new SuppressionDocument(written).problems().isEmpty());
        Path partial = Files.createDirectories(directory.resolve("partial"));
        String checkstyleOnly = Files.readString(
            ScannerResources.copyApplicable(partial, List.of(checkstyle))
        );
        assertTrue(checkstyleOnly.contains("maven-checkstyle-plugin"));
        assertFalse(checkstyleOnly.contains("maven-pmd-plugin"));
        String pmd = "pkg:maven/org.apache.maven.plugins/maven-pmd-plugin@3.28.0?type=jar";
        Path java = Files.createDirectories(directory.resolve("java"));
        Path shipped = ScannerResources.copy("tool-suppressions.xml", Files.createDirectories(directory.resolve("x")));
        assertEquals(
            Files.readString(shipped),
            Files.readString(ScannerResources.copyApplicable(java, List.of(checkstyle, pmd))),
            "a Java module that resolves both wrappers keeps the file as shipped"
        );
    }
}
