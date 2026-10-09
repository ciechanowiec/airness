package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.SuppressionDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
}
