package eu.ciechanowiec.airness.governance;

import static eu.ciechanowiec.airness.governance.ActionRuntimes.CHECKOUT;
import static eu.ciechanowiec.airness.governance.ActionRuntimes.MISSING;
import static eu.ciechanowiec.airness.governance.ActionRuntimes.NODE20;
import static eu.ciechanowiec.airness.governance.ActionRuntimes.WORKFLOW;
import static eu.ciechanowiec.airness.governance.ActionRuntimes.check;
import static eu.ciechanowiec.airness.governance.ActionRuntimes.composite;
import static eu.ciechanowiec.airness.governance.ActionRuntimes.outdatedIn;
import static eu.ciechanowiec.airness.governance.ActionRuntimes.steps;
import static eu.ciechanowiec.airness.governance.ActionRuntimes.unreadIn;
import static eu.ciechanowiec.airness.governance.ActionRuntimes.workflow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * What the Node runtime rule passes over, what it cannot judge and refuses rather than passes, and how it reads
 * the metadata it judges: each action once, and a source that cannot answer stopping the check rather than
 * reporting the action.
 */
class ActionRuntimeReadingTest {

    @Test
    void cannotJudgeCompositesNestedDeeperThanGithubRuns() {
        int depth = ActionRuntimeCheck.NESTING_LIMIT + 1;
        Map<String, String> chain = IntStream.range(0, depth).boxed().collect(
            Collectors.toMap("octo/level@v%d"::formatted, level -> composite("octo/level@v%d".formatted(level + 1)))
        );
        ActionRuntimeCheck check = workflow("runtime-deep", steps("octo/level@v0"), chain);
        assertEquals(
            List.of(
                WORKFLOW + ":5: octo/level@v0 - runs " + "octo/level@v%d".formatted(ActionRuntimeCheck.NESTING_LIMIT)
                    + ", which cannot be read: nested deeper than the " + ActionRuntimeCheck.NESTING_LIMIT
                    + " composite actions GitHub runs; use a release of octo/level whose actions can all be read"
            ),
            unreadIn(check)
        );
    }

    @Test
    void passesOverLocalAndDockerActionsAndReusableWorkflows() {
        String yaml = """
            jobs:
              build:
                runs-on: ubuntu-26.04
                steps:
                  - uses: ./.github/actions/install-maven
                  - uses: docker://alpine:3.21
              call:
                uses: octo/flows/.github/workflows/build.yml@v1
            """;
        RecordedMetadata source = new RecordedMetadata(Map.of());
        Path root = new GitFixture("runtime-skipped").write(WORKFLOW, yaml).root();
        ActionRuntimeCheck check = new ActionRuntimeCheck(root, source);
        assertTrue(Verdicts.clean(check.findings()), () -> String.valueOf(check.findings()));
        assertEquals(List.of(), source.asked(), "nothing is looked up");
    }

    @Test
    void cannotJudgeAnActionWhoseRevisionHoldsNoMetadata() {
        ActionRuntimeCheck check = workflow("runtime-missing", steps("octo/gone@v1"), Map.of());
        assertEquals(
            List.of(WORKFLOW + ":5: octo/gone@v1 - " + MISSING + "octo/gone that holds the action"), unreadIn(check)
        );
        assertEquals(List.of(), outdatedIn(check));
    }

    @Test
    void cannotJudgeAnExpressionOrAMalformedValue() {
        ActionRuntimeCheck check = workflow(
            "runtime-expression", steps("${{ matrix.action }}", "octo-checkout"), Map.of()
        );
        assertEquals(
            List.of(
                WORKFLOW + ":5: ${{ matrix.action }} - an expression, which GitHub never substitutes in a uses key; use"
                    + " the action written literally",
                WORKFLOW + ":6: octo-checkout - not written in the form GitHub reads an action in; use "
                    + ActionUses.FORM
            ),
            unreadIn(check)
        );
    }

    @Test
    void cannotJudgeMetadataThatDeclaresNoRuntimeGithubRuns() {
        ActionRuntimeCheck check = workflow(
            "runtime-unknown", steps("octo/odd@v1", "octo/bare@v1"),
            Map.of("octo/odd@v1", "runs:\n  using: deno2\n", "octo/bare@v1", "name: bare\n")
        );
        assertEquals(
            List.of(
                WORKFLOW + ":5: octo/odd@v1 - it declares deno2, not a runtime GitHub runs actions on; use a release"
                    + " of octo/odd that declares node24 or later, docker, or composite",
                WORKFLOW + ":6: octo/bare@v1 - its metadata declares no runs.using; use a release of octo/bare that"
                    + " does"
            ),
            unreadIn(check)
        );
    }

    @Test
    void cannotJudgeACompositeWhoseStepCannotBeReadOrIsMissing() {
        ActionRuntimeCheck check = workflow(
            "runtime-composite-unread", steps("octo/outer@v1"),
            Map.of("octo/outer@v1", composite("${{ inputs.action }}", "octo/gone@v1"))
        );
        assertEquals(
            List.of(
                WORKFLOW + ":5: octo/outer@v1 - a step uses ${{ inputs.action }}, an expression, which GitHub never"
                    + " substitutes in a uses key; use a release of octo/outer whose steps can be read",
                WORKFLOW + ":5: octo/outer@v1 - runs octo/gone@v1, which cannot be read: " + MISSING.substring(
                    0, MISSING.indexOf("; use")
                ) + "; use a release of octo/outer whose actions can all be read"
            ),
            unreadIn(check)
        );
    }

    @Test
    void failsRatherThanReportingWhenTheSourceCannotAnswer() {
        Path root = new GitFixture("runtime-outage").write(WORKFLOW, steps(CHECKOUT)).root();
        IllegalStateException failure = assertThrows(
            IllegalStateException.class,
            () -> new ActionRuntimeCheck(
                root, _ -> {
                    throw new IllegalStateException("GitHub could not be read");
                }
            )
        );
        assertEquals("GitHub could not be read", failure.getMessage());
    }

    @Test
    void readsTheMetadataOfAnActionUsedTwiceOnce() {
        Path root = new GitFixture("runtime-once")
            .write(WORKFLOW, steps(CHECKOUT, CHECKOUT))
            .write(".github/workflows/other.yaml", steps(CHECKOUT))
            .root();
        RecordedMetadata source = new RecordedMetadata(Map.of(CHECKOUT, NODE20));
        ActionRuntimeCheck check = new ActionRuntimeCheck(root, source);
        assertEquals(1, source.asked().size());
        assertEquals(3, outdatedIn(check).size());
        assertEquals(2, check.scanned());
    }

    @Test
    void findsNothingInARepositoryWithoutWorkflows() {
        ActionRuntimeCheck check = check(new GitFixture("runtime-none").write("README.md", "# x\n").root(), Map.of());
        assertEquals(0, check.scanned());
        assertTrue(Verdicts.clean(check.findings()));
        assertEquals(2, check.findings().size());
    }
}
