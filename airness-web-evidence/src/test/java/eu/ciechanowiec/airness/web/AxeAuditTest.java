package eu.ciechanowiec.airness.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import lombok.SneakyThrows;
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

    // The rules the options file switches on, read off the file rather than off a second list.
    private static final Pattern ENABLED_RULE = Pattern.compile("\"([a-z-]+)\": \\{ \"enabled\": true }");

    // The one options file the npm package ships, which the build packages beside this class.
    private static final Path SHIPPED_OPTIONS = Path.of("..", "airness-node", "configs", "axe-options.json");

    @Test
    void asksForEveryVerdictRatherThanForTheFailuresAlone() {
        assertTrue(
            AxeAudit.SCRIPT.contains("axe.run(document, {rules: rules, checks: checks})"),
            "the rules are asked about the whole document, with the rule switches and check options of the file"
        );
        assertFalse(
            AxeAudit.SCRIPT.contains("resultTypes"),
            "and no result type is named, because naming one leaves the rest of the answer empty"
        );
    }

    @Test
    void sizesNoTargetThatTheVisuallyHiddenPatternClipsToAPixel() {
        assertTrue(
            AxeAudit.SCRIPT.contains("rule.id === 'target-size'")
                && AxeAudit.SCRIPT.contains("box.width <= 1 || box.height <= 1")
                && AxeAudit.SCRIPT.contains("report.violations.flatMap(rule => reported(rule).map("),
            "a target-size failure on a box of a pixel or less, which no pointer reaches, is left out"
        );
        assertTrue(
            AxeAudit.SCRIPT.contains("report.incomplete.flatMap(rule => rule.nodes.map("),
            "while every verdict the rules could not reach is still read whole"
        );
    }

    @Test
    @SneakyThrows
    void appliesTheOptionsFileTheBrowserSweepReads() {
        assertEquals(
            Files.readString(SHIPPED_OPTIONS), AxeAudit.OPTIONS,
            "the audit packages the file the npm package ships, byte for byte, rather than a copy of it"
        );
        assertTrue(
            AxeAudit.SCRIPT.contains("const options = " + AxeAudit.OPTIONS + ";"),
            "and the question hands the rules that file whole"
        );
    }

    @Test
    void turnsOnTheRulesTheRulesShipSwitchedOff() {
        assertEquals(
            List.of(
                "identical-links-same-purpose", "meta-refresh-no-exceptions", "target-size", "css-orientation-lock",
                "p-as-heading", "td-has-header", "table-fake-caption", "color-contrast-enhanced"
            ),
            enabledRules(),
            "the AAA, target-size and experimental rules the defaults leave out, and the enhanced contrast a "
                + "project that raises the contrast of text is held to besides"
        );
    }

    @Test
    void addsToTheDefaultRulesRatherThanReplacingThem() {
        assertFalse(
            AxeAudit.OPTIONS.contains("\"runOnly\":"),
            "naming the rules to run alone would drop every default rule the list leaves out"
        );
        assertFalse(
            enabledRules().contains("hidden-content") || enabledRules().contains("focus-order-semantics"),
            "and the experimental rules that report only items to look at by eye stay off"
        );
    }

    @Test
    void namesOnlyRulesThePinnedLibraryDefines() {
        String library = AxeLibrary.rules();
        enabledRules().forEach(
            rule -> assertTrue(
                library.contains("id:\"%s\"".formatted(rule)),
                "a rule the library does not define would be switched on silently and run nowhere: " + rule
            )
        );
    }

    @Test
    void measuresTheTargetThroughTheChecksThePinnedLibraryNames() {
        String library = AxeLibrary.rules();
        assertTrue(
            library.contains(
                "any:[{options:{minSize:24},id:\"target-size\"},{options:{minOffset:24},id:\"target-offset\"}]"
            ),
            "the target-size rule is made of the two checks the options name, under the option names they read"
        );
        assertTrue(
            library.contains(
                "id:\"target-size\",impact:\"serious\",selector:\"*\",enabled:!1,"
                    + "matches:\"widget-not-inline-matches\""
            ),
            "and it selects no target set in a line of text, so the inline exception holds"
        );
    }

    @Test
    void refusesToAskWithoutTheOptionsFile() {
        IllegalStateException refused = assertThrows(
            IllegalStateException.class,
            () -> AxeAudit.options("eu/ciechanowiec/airness/web/absent.json"),
            "a question without the file would hold a page to the default rules alone"
        );
        assertEquals(
            "The accessibility options are not packaged with the audit, so it would ask fewer rules than the "
                + "browser sweep: eu/ciechanowiec/airness/web/absent.json",
            refused.getMessage(), "and the refusal says what is missing"
        );
    }

    private static List<String> enabledRules() {
        return ENABLED_RULE.matcher(AxeAudit.OPTIONS).results().map(found -> found.group(1)).toList();
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
