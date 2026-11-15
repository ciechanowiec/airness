package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.ReportAdvisory;
import eu.ciechanowiec.airness.governance.ReportMitigation;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * A repair is printed while a finding it answers is open, and not once the project's allowlist mitigated each one.
 */
class ImageRepairTest {

    private static final ReportAdvisory OPEN = new ReportAdvisory(
        "GHSA-undici", "HIGH", "pkg:npm/undici@6.28.0", List.of("shop"), "Request smuggling", Optional.empty()
    );
    private static final ReportAdvisory MITIGATED = new ReportAdvisory(
        "GHSA-glob", "MEDIUM", "pkg:npm/glob@13.0.6", List.of("shop"), "Path traversal",
        Optional.of(new ReportMitigation("Unused at runtime.", "Removed with the base.", "2026-11-15", false))
    );

    @Test
    void isNeededOnlyWhileAFindingItAnswersIsOpen() {
        ImageRepair undici = new ImageRepair("replace FROM", Set.of(OPEN.key()));
        ImageRepair glob = new ImageRepair("remove glob", Set.of(MITIGATED.key()));
        assertAll(
            () -> assertTrue(undici.needed(List.of(OPEN, MITIGATED))),
            () -> assertFalse(glob.needed(List.of(OPEN, MITIGATED)), "the allowlist answered its only finding"),
            () -> assertFalse(undici.needed(List.of(MITIGATED)), "a finding that no longer exists needs no repair")
        );
    }
}
