package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies the immutable evidence and report text shared by governance checks.
 */
class FindingsTest {

    @Test
    void findingsDefensivelyCopyAndRenderTheirEvidence() {
        List<String> mutable = new ArrayList<>(List.of("first", "second"));
        Findings findings = new Findings("Broken rule", mutable);
        mutable.clear();
        assertEquals(List.of("first", "second"), findings.offences());
        assertEquals("Broken rule (2):%n  first%n  second".formatted(), findings.report());
    }

    @Test
    void rendersAnOwnedVersionUpdate() {
        DeclaredCoordinate coordinate = new DeclaredCoordinate("sample", "library", "1.0.0");
        VersionUpdate update = new VersionUpdate(new OwnedCoordinate("sample:app", coordinate), "2.0.0");
        assertEquals("[sample:app] sample:library 1.0.0 -> 2.0.0", update.report());
    }
}
