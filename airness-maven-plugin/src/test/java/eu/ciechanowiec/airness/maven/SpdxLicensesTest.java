package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * An npm declaration is judged by SPDX identifiers only, regardless of case, and a known exception leaves its
 * license to be judged alone.
 */
class SpdxLicensesTest {

    @Test
    void spellsAnIdentifierTheSpdxWay() {
        assertEquals("BlueOak-1.0.0", SpdxLicenses.spelling("blueoak-1.0.0"));
        assertEquals("Apache-2.0", SpdxLicenses.spelling("apache-2.0+"), "or later reaches the license itself");
        assertEquals("CC0-1.0", SpdxLicenses.spelling("cc0-1.0"));
    }

    @Test
    void neverReadsAFreeTextNameAsALicense() {
        assertTrue(SpdxLicenses.spelling("BSD").startsWith("not an SPDX license"), "BSD may be the 4-clause BSD");
        assertTrue(SpdxLicenses.spelling("Public").startsWith("not an SPDX license"));
        assertEquals(List.of("not an SPDX license: Bouncy"), SpdxLicenses.alternatives("Bouncy"));
    }

    @Test
    void judgesALicenseWithAKnownExceptionAsTheLicense() {
        assertTrue(SpdxLicenses.exception("llvm-exception"));
        assertFalse(SpdxLicenses.exception("Made-Up-exception"));
        assertEquals(List.of("Apache-2.0"), SpdxLicenses.alternatives("Apache-2.0 WITH LLVM-exception"));
        assertEquals(
            List.of("Apache-2.0 WITH Made-Up-exception"), SpdxLicenses.alternatives(
                "Apache-2.0 WITH Made-Up-exception"
            ),
            "an unknown exception keeps the declaration whole, which nothing admits"
        );
    }
}
