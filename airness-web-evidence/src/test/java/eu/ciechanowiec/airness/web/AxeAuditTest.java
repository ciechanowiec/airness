package eu.ciechanowiec.airness.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The question asks for every verdict the rules report, and the reading turns what a browser answered
 * into the two that are not a pass.
 *
 * <p>The answers below are shaped exactly as a driver returns them, which is a list of strings, so the
 * reading is exercised against what it will actually be handed rather than against a convenience.
 */
class AxeAuditTest {

    private static final String FAILED
        = "failed\tcolor-contrast\t.btn\tElement has insufficient colour contrast";

    private static final String UNDECIDED
        = "undecided\tcolor-contrast\t.select\tbackground could not be determined";

    @Test
    void asksForEveryVerdictRatherThanForTheFailuresAlone() {
        assertTrue(
            AxeAudit.SCRIPT.contains("axe.run(document)"),
            "the rules are asked about the whole document"
        );
        assertFalse(
            AxeAudit.SCRIPT.contains("resultTypes"),
            "and no result type is named, because naming one leaves the rest of the answer empty"
        );
    }

    @Test
    void readsBothTheFailuresAndTheVerdictsTheRulesCouldNotReach() {
        AxeVerdict verdict = AxeAudit.of(List.of(FAILED, UNDECIDED));
        assertEquals(1, verdict.failed().size(), "the rule that decided the page was wrong is read");
        assertEquals(1, verdict.undecided().size(), "and so is the rule that could not tell");
    }

    @Test
    void reportsAVerdictItCouldNotReachAsAProblem() {
        List<String> problems = AxeAudit.of(List.of(UNDECIDED)).problems();
        assertEquals(1, problems.size(), "a rule that could not decide is a case nobody has checked");
        assertTrue(problems.getFirst().startsWith("undecided "), "and it is reported as what it is");
    }

    @Test
    void reportsNothingForAVerdictAnAcceptanceAccountsFor() {
        AxeVerdict verdict = AxeAudit.of(
            List.of(UNDECIDED),
            List.of(new AxeExemption("color-contrast", ".select", "measured 17.53 to 1 by hand"))
        );
        assertEquals(List.of(), verdict.problems(), "an undecidable somebody measured is accounted for");
    }

    @Test
    void reportsAFailureEvenWhereAnAcceptanceNamesTheSameRuleAndElement() {
        AxeVerdict verdict = AxeAudit.of(
            List.of("failed\tcolor-contrast\t.select\tElement has insufficient colour contrast", UNDECIDED),
            List.of(new AxeExemption("color-contrast", ".select", "measured 17.53 to 1 by hand"))
        );
        List<String> problems = verdict.problems();
        assertEquals(
            1, problems.size(),
            "accepting what a rule could not decide accepts nothing it decided against"
        );
        assertTrue(
            problems.getFirst().startsWith("failed "),
            "and what is left is the failure rather than the undecidable that was accepted"
        );
    }

    @Test
    void readsAnswersInTheOrderTheBrowserGaveThem() {
        AxeVerdict verdict = AxeAudit.of(List.of(FAILED, UNDECIDED));
        assertEquals(
            ".btn", verdict.failed().getFirst().target(), "the element the failing rule named is kept"
        );
        assertEquals(
            ".select", verdict.undecided().getFirst().target(), "and so is the one it could not read"
        );
    }

    @Test
    void reportsALineItCannotReadRatherThanDroppingIt() {
        List<String> problems = AxeAudit.of(List.of("something nobody wrote in four fields")).problems();
        assertEquals(1, problems.size(), "an answer in an unexpected shape is still an answer");
        assertTrue(
            problems.getFirst().contains("unreadable-answer"),
            "and it says that the shape rather than the page is what went wrong"
        );
    }

    @Test
    void readsAFailureOfTheRulesThemselvesAsAFailure() {
        List<String> problems = AxeAudit
            .of(List.of("failed\taxe-did-not-run\tdocument\tScript timed out"))
            .problems();
        assertTrue(
            problems.getFirst().contains("axe-did-not-run"),
            "rules that never ran are a failure rather than a clean page"
        );
    }

    @Test
    void refusesAnAnswerThatIsNotAListOfLines() {
        IllegalStateException refused = assertThrows(
            IllegalStateException.class,
            () -> AxeAudit.of("the browser returned a bare string"),
            "an answer with no lines in it says the rules did not run"
        );
        assertEquals(
            "The accessibility rules answered with nothing to read, which says they did not run "
                + "rather than that the page is clean",
            refused.getMessage(), "and the refusal says so rather than reporting a clean page"
        );
    }

    @Test
    void refusesAnAnswerOfNothingAtAll() {
        assertThrows(
            IllegalStateException.class,
            () -> AxeAudit.of(null),
            "a driver answering nothing is the rules not reporting rather than a page with nothing wrong"
        );
    }

    @Test
    void answersNoProblemForAPageThatBreaksNothing() {
        assertEquals(
            List.of(), AxeAudit.of(List.of()).problems(), "a page the rules found nothing on is clean"
        );
    }
}
