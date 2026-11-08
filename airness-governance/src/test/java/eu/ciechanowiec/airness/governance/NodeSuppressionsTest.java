package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
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
    void countsEachRuleAnInlineConfigurationNames() {
        String source = """
            /* eslint max-lines: "off" -- the generated table is long */
            /*eslint complexity:0*/
            /* eslint no-console: ["error", { allow: ["warn", "log"] }], max-depth: [2, 10] */
            /* eslint "@typescript-eslint/no-explicit-any": 'off', 'max-params': "off" */
            /*
              eslint
                no-alert: off,
                no-debugger: off
            */
            """;
        assertEquals(
            List.of(
                "line 1: eslint max-lines", "line 2: eslint complexity", "line 3: eslint no-console",
                "line 3: eslint max-depth", "line 4: eslint @typescript-eslint/no-explicit-any",
                "line 4: eslint max-params", "line 5: eslint no-alert", "line 5: eslint no-debugger"
            ),
            NodeSuppressions.in(source),
            "an inline configuration switches off or loosens each rule it names, whatever its options carry"
        );
    }

    @Test
    void readsNoInlineConfigurationWhereESLintReadsNone() {
        String source = """
            /* eslint-disable */
            /* eslint-env node */
            // eslint max-lines: off
            /** eslint max-lines: off */
            """;
        assertEquals(
            List.of("line 1: eslint-disable (every rule)"),
            NodeSuppressions.in(source),
            "a disable comment counts once, and ESLint reads an inline configuration only in a plain block comment"
        );
    }

    @Test
    void readsATypeScriptDirectiveInEveryCommentTheCompilerHonours() {
        String source = """
            /** @ts-expect-error the type is wrong upstream */
            /*** @ts-ignore */
            /// @ts-ignore
            """;
        assertEquals(
            List.of("line 1: @ts-expect-error", "line 2: @ts-ignore", "line 3: @ts-ignore"),
            NodeSuppressions.in(source),
            "the compiler reads a directive after any run of slashes or stars that opens the comment"
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
    void countsCoverageDirectivesTheSecretMarkerAndTheLayoutExemption() {
        String source = """
            /* v8 ignore next */
            /* v8 ignore stop */
            // c8 ignore start
            /* istanbul ignore if */
            const key = 'fixture' // gitleaks:allow
            const box = <div data-airness-layout="overlap" />
            """;
        assertEquals(
            List.of(
                "line 1: v8 ignore", "line 3: c8 ignore", "line 4: istanbul ignore", "line 5: gitleaks:allow",
                "line 6: data-airness-layout="
            ),
            NodeSuppressions.in(source),
            "the stop of a range was counted at its start"
        );
    }
}
