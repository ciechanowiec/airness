package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SnapshotVersionsTest {

    @Test
    void recognizesASnapshot() {
        assertTrue(SnapshotVersions.snapshot("1.0.11-SNAPSHOT"));
    }

    @Test
    void readsAReleaseAndAnAbsentVersionAsNoSnapshot() {
        assertFalse(SnapshotVersions.snapshot("1.0.10"));
        assertFalse(SnapshotVersions.snapshot(null));
    }
}
