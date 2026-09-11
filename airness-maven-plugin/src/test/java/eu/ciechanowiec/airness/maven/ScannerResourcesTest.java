package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScannerResourcesTest {

    @Test
    @SneakyThrows
    void readsProducerResourcesAndRefusesMissingOnes(@TempDir Path directory) {
        Path policy = ScannerResources.copy("checkov-policy.tsv", directory);
        assertTrue(Files.readString(policy).contains("CKV_DOCKER_2"));
        assertThrows(IOException.class, () -> ScannerResources.copy("not-a-resource", directory));
    }
}
