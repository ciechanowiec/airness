package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class ScannerToolsTest {

    @Test
    void requiresFilteredProducerPinsAndRejectsUnknownTools() {
        assertTrue(ScannerTools.image("checkov").contains("@sha256:"));
        assertThrows(IllegalArgumentException.class, () -> ScannerTools.image("unknown"));
        Properties pins = new Properties();
        pins.setProperty("checkov", "${checkov.image}");
        assertThrows(IllegalArgumentException.class, () -> ScannerTools.image(pins, "checkov"));
    }
}
