package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Every directive the node gates honour counts once per rule it turns off, and only where the tools read it.
 */
class NodeSuppressionsTest {

    @Test
    void countsEachRuleADisableCommentNamesAndNotItsReason() {
        assertEquals(
            List.of("line 1: eslint-disable-next-line no-console", "line 1: eslint-disable-next-line no-alert"),
            NodeSuppressions.in("// eslint-disable-next-line no-console, no-alert -- the reason, with a comma\n"),
            "two rules named, and the reason after -- is prose"
        );
    }

    @Test
    void countsADisableCommentNamingNoRuleOnce() {
        assertEquals(
            List.of("line 2: eslint-disable (every rule)", "line 3: oxlint-disable-line (every rule)"),
            NodeSuppressions.in("const a = 1\n/* eslint-disable */\nconst b = 2 // oxlint-disable-line\n")
        );
    }

    @Test
    void readsStylelintBiomeAndTypeScriptDirectives() {
        List<String> found = NodeSuppressions.in(
            """
                /* stylelint-disable-line color-named */
                // biome-ignore lint/style/noNonNullAssertion: the map was filled above
                // @ts-expect-error the type is wrong upstream
                /* @ts-nocheck */
                """
        );
        assertEquals(
            List.of(
                "line 1: stylelint-disable-line color-named",
                "line 2: biome-ignore lint/style/noNonNullAssertion",
                "line 3: @ts-expect-error", "line 4: @ts-nocheck"
            ),
            found
        );
    }

    @Test
    void readsADirectiveOnlyAtTheStartOfAComment() {
        assertEquals(
            List.of(),
            NodeSuppressions.in("const text = 'eslint-disable no-console'\n// see eslint-disable-next-line docs\n"),
            "a directive quoted in a string or mentioned in prose turns nothing off"
        );
    }

    @Test
    void readsOnlyTheSourcesTheDirectivesAppearIn() {
        assertTrue(NodeSuppressions.readable("src/view.TSX"));
        assertTrue(NodeSuppressions.readable("styles/site.scss"));
        assertFalse(NodeSuppressions.readable("README.md"));
        assertFalse(NodeSuppressions.readable(".eslintrc"));
    }

    @Test
    void countsCoverageDirectivesTheSecretMarkerTheLayoutExemptionAndALayerForm() {
        String source = """
            /* v8 ignore next */
            /* v8 ignore stop */
            // c8 ignore start
            /* istanbul ignore if */
            const key = 'fixture' // gitleaks:allow
            const box = <div data-airness-layout="overlap" />
            // payload-ignore: generated
            """;
        assertEquals(
            List.of(
                "line 1: v8 ignore", "line 3: c8 ignore", "line 4: istanbul ignore", "line 5: gitleaks:allow",
                "line 6: data-airness-layout=", "line 7: payload-ignore"
            ),
            NodeSuppressions.in(source, List.of(Pattern.compile("payload-ignore"))),
            "the stop of a range was counted at its start"
        );
    }
}
