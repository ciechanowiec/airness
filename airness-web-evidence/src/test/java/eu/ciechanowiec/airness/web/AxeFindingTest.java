package eu.ciechanowiec.airness.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A finding writes itself as one line naming the rule, the element and what to do.
 */
class AxeFindingTest {

    @Test
    void writesTheRuleTheElementAndTheRepair() {
        String line = new AxeFinding("color-contrast", ".btn", "Increase the contrast").worded("failed");
        assertTrue(line.startsWith("failed "), "the verdict leads, so a report sorts by what happened");
        assertTrue(line.contains("color-contrast"), "the rule is named");
        assertTrue(line.contains(".btn"), "the element is named");
        assertTrue(line.contains("Increase the contrast"), "and what to do about it");
    }

    @Test
    void answersWhetherAnAcceptanceReachesIt() {
        AxeFinding finding = new AxeFinding("color-contrast", "#standing .select", "could not tell");
        assertTrue(
            finding.coveredBy(new AxeExemption("color-contrast", ".select", "measured by hand")),
            "a finding is covered by an acceptance written for it"
        );
        assertFalse(
            finding.coveredBy(new AxeExemption("color-contrast", ".badge", "measured by hand")),
            "and by no other"
        );
    }
}
