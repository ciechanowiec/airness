package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * One allowlist judges a Maven declaration and an npm expression: an alternative joining licenses with AND is
 * admitted only when every one of them is, and an SPDX identifier is matched however it is cased.
 */
class ReportLicensePolicyTest {

    private static final ReportLicensePolicy POLICY = new ReportLicensePolicy(
        Set.of("MIT", "Apache-2.0", "LGPL-2.1", "BlueOak-1.0.0"), Map.of(
            "LGPL-2.1+", "LGPL-2.1", "MIT License", "MIT", "COMMON DEVELOPMENT AND DISTRIBUTION LICENSE", "LGPL-2.1"
        )
    );

    @Test
    void admitsAConjunctionOnlyWhenItAdmitsEveryLicenseInIt() {
        assertEquals("Permissive", POLICY.classification(List.of("MIT AND Apache-2.0")));
        assertEquals("Outside policy", POLICY.classification(List.of("MIT AND GPL-3.0-only")));
        assertEquals("Permissive", POLICY.classification(List.of("MIT AND GPL-3.0-only", "Apache-2.0")));
        assertEquals(
            "Weak copyleft", POLICY.classification(List.of("MIT AND LGPL-2.1")),
            "an alternative carrying a weak copyleft license carries its condition"
        );
    }

    @Test
    void keepsALicenseNameContainingAndWhole() {
        assertEquals(
            "Weak copyleft", POLICY.classification(List.of("COMMON DEVELOPMENT AND DISTRIBUTION LICENSE")),
            "a spelling the merges carry is one license, not a conjunction of two names"
        );
    }

    @Test
    void refusesWhatTheAllowlistDoesNotAdmit() {
        assertTrue(POLICY.refuses(new ReportPackage("pkg:npm/x@1", List.of("SSPL-1.0"), List.of(), Map.of())));
        assertFalse(POLICY.refuses(new ReportPackage("pkg:npm/y@1", List.of("MIT License"), List.of(), Map.of())));
    }
}
