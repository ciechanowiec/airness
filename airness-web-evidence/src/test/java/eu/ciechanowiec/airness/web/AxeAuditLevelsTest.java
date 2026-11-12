package eu.ciechanowiec.airness.web;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// The levels a project holds the size of a pointer target and the contrast of text to: AA, which the shipped
// question asks at, and AAA, which the question a project that raised a rule asks at adds from the same file.
class AxeAuditLevelsTest {

    @Test
    void asksAtTheAaLevelUnlessAProjectRaisesARule() {
        assertTrue(
            AxeAudit.SCRIPT.contains("const levels = {\"targetSize\": \"AA\", \"contrast\": \"AA\"};"),
            "the shipped question holds both costly rules to AA, the level every project is held to"
        );
        assertTrue(
            AxeAudit.script(AccessibilityLevel.AAA, AccessibilityLevel.AA)
                .contains("const levels = {\"targetSize\": \"AAA\", \"contrast\": \"AA\"};"),
            "and a project that raises one rule asks at the level it named for that rule alone"
        );
        assertTrue(
            AxeAudit.SCRIPT.contains(".map(rule => options.levels[rule][levels[rule]])"),
            "what a raised level holds is read from the levels of the file the sweep reads"
        );
    }

    @Test
    void measuresARaisedTargetAtTheEnhancedSize() {
        assertTrue(
            AxeAudit.OPTIONS.contains("\"target-size\": { \"options\": { \"minSize\": 44 } }"),
            "the AAA size is handed to the check that measures a target a project raised to it"
        );
        assertTrue(
            AxeAudit.OPTIONS.contains("\"target-offset\": { \"enabled\": false }"),
            "and the room around a target excuses no smaller one, which only the AA size allows"
        );
    }
}
