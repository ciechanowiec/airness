package eu.ciechanowiec.airness.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * An accepted undecidable names a rule, an element and a reason, matches the finding it was written
 * for, and says so when it no longer matches anything.
 */
class AxeExemptionTest {

    private static final String RULE = "color-contrast";

    private static final String ELEMENT = ".select";

    private static final String WHY = "the arrow is two gradients; measured 17.53 to 1 by hand";

    @Test
    void coversTheFindingItWasWrittenFor() {
        AxeExemption exemption = new AxeExemption(RULE, ELEMENT, WHY);
        assertTrue(
            exemption.covers(new AxeFinding(RULE, "#standing .select", "could not tell")),
            "a reported selector carries the path it was found by, and the exemption names part of it"
        );
    }

    @Test
    void coversNothingUnderAnotherRule() {
        AxeExemption exemption = new AxeExemption(RULE, ELEMENT, WHY);
        assertFalse(
            exemption.covers(new AxeFinding("aria-valid-attr", "#standing .select", "could not tell")),
            "accepting one rule on an element accepts nothing else about it"
        );
    }

    @Test
    void coversNothingOnAnotherElement() {
        AxeExemption exemption = new AxeExemption(RULE, ELEMENT, WHY);
        assertFalse(
            exemption.covers(new AxeFinding(RULE, ".badge", "could not tell")),
            "accepting a rule on one element accepts it nowhere else"
        );
    }

    @Test
    void namesWhatItNoLongerExcuses() {
        String stale = new AxeExemption(RULE, ELEMENT, WHY).stale();
        assertTrue(stale.contains(RULE), "the line names the rule that was accepted");
        assertTrue(stale.contains(ELEMENT), "and the element it was accepted for");
        assertTrue(stale.contains("remove it"), "and what to do about it now");
    }

    @Test
    void refusesAnAcceptanceThatGivesNoReason() {
        IllegalArgumentException refused = assertThrows(
            IllegalArgumentException.class,
            () -> new AxeExemption(RULE, ELEMENT, " "),
            "an acceptance without a reason is a rule turned off"
        );
        assertEquals(
            "An accepted undecidable names a rule, an element and the reason it was accepted",
            refused.getMessage(), "and the refusal says what is missing"
        );
    }

    @Test
    void refusesAnAcceptanceThatNamesNoRule() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new AxeExemption("", ELEMENT, WHY),
            "an acceptance naming no rule accepts every rule"
        );
    }

    @Test
    void refusesAnAcceptanceThatNamesNoElement() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new AxeExemption(RULE, "", WHY),
            "an acceptance naming no element accepts the rule everywhere"
        );
    }

    @Test
    void keepsTheReasonItWasGiven() {
        assertEquals(
            WHY, new AxeExemption(RULE, ELEMENT, WHY).reason(),
            "the measurement is kept where the next reader will find it"
        );
    }
}
