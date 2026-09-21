package eu.ciechanowiec.airness.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A verdict answers three kinds of problem as one list, and holds what it was given.
 */
class AxeVerdictTest {

    private static final AxeFinding FAILURE
        = new AxeFinding("color-contrast", ".btn", "Element has insufficient colour contrast");

    private static final AxeFinding UNDECIDED
        = new AxeFinding("color-contrast", ".select", "background could not be determined");

    private static final AxeExemption ACCEPTED
        = new AxeExemption("color-contrast", ".select", "measured 17.53 to 1 by hand");

    @Test
    void reportsAnAcceptanceThatNoLongerAnswersToAnything() {
        AxeVerdict verdict = new AxeVerdict(List.of(), List.of(), List.of(ACCEPTED));
        assertEquals(1, verdict.problems().size(), "an excuse outlived the thing it excused");
        assertTrue(
            verdict.problems().getFirst().contains("no longer answers"),
            "and the report says that rather than naming a defect of the page"
        );
    }

    @Test
    void reportsAllThreeKindsTogether() {
        AxeVerdict verdict = new AxeVerdict(
            List.of(FAILURE), List.of(UNDECIDED),
            List.of(new AxeExemption("region", ".nowhere", "measured by hand"))
        );
        assertEquals(
            3, verdict.problems().size(),
            "a failure, an unaccounted undecidable and a stale acceptance are each a problem"
        );
    }

    @Test
    void keepsALiveAcceptanceOutOfTheReport() {
        AxeVerdict verdict = new AxeVerdict(List.of(), List.of(UNDECIDED), List.of(ACCEPTED));
        assertEquals(List.of(), verdict.problems(), "an acceptance that still answers is not a problem");
    }

    @Test
    void holdsItsOwnCopyOfWhatItWasGiven() {
        List<AxeFinding> given = new ArrayList<>(List.of(FAILURE));
        AxeVerdict verdict = new AxeVerdict(given, List.of(), List.of());
        given.clear();
        assertEquals(
            1, verdict.failed().size(), "what a verdict says cannot be changed after it is handed over"
        );
    }

    @Test
    void assemblesFromTheCollectionsTheReadingProduced() {
        AxeVerdict verdict = AxeVerdict.of(List.of(FAILURE), List.of(UNDECIDED), List.of(ACCEPTED));
        assertEquals(1, verdict.failed().size(), "the failures it was assembled from are kept");
        assertEquals(1, verdict.accepted().size(), "and so are the acceptances");
    }
}
