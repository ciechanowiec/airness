package eu.ciechanowiec.airness.governance;

import static eu.ciechanowiec.airness.governance.ActionRuntimes.CHECKOUT;
import static eu.ciechanowiec.airness.governance.ActionRuntimes.COMMIT;
import static eu.ciechanowiec.airness.governance.ActionRuntimes.CURRENT_CHECKOUT;
import static eu.ciechanowiec.airness.governance.ActionRuntimes.DEPRECATED;
import static eu.ciechanowiec.airness.governance.ActionRuntimes.NODE20;
import static eu.ciechanowiec.airness.governance.ActionRuntimes.NODE24;
import static eu.ciechanowiec.airness.governance.ActionRuntimes.WORKFLOW;
import static eu.ciechanowiec.airness.governance.ActionRuntimes.check;
import static eu.ciechanowiec.airness.governance.ActionRuntimes.composite;
import static eu.ciechanowiec.airness.governance.ActionRuntimes.outdated;
import static eu.ciechanowiec.airness.governance.ActionRuntimes.outdatedIn;
import static eu.ciechanowiec.airness.governance.ActionRuntimes.steps;
import static eu.ciechanowiec.airness.governance.ActionRuntimes.unreadIn;
import static eu.ciechanowiec.airness.governance.ActionRuntimes.workflow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Every action a workflow or a composite action uses declares a supported Node runtime or none, judged from the
 * metadata at the ref the action is pinned to, wherever the {@code uses} key stands and however its value is
 * quoted, and through every composite action it reaches.
 */
class ActionRuntimeCheckTest {

    @Test
    void refusesAnActionPinnedToACommitThatDeclaresNode20() {
        ActionRuntimeCheck check = workflow(
            "runtime-node20", steps(CHECKOUT + " # v4.2.2"), Map.of(CHECKOUT, NODE20)
        );
        assertEquals(List.of(outdated(WORKFLOW + ":5", CHECKOUT, "node20")), outdatedIn(check));
        assertEquals(List.of(), unreadIn(check));
    }

    @Test
    void refusesEveryNodeRuntimeBelowTheRequiredOne() {
        ActionRuntimeCheck check = workflow(
            "runtime-old", steps("octo/twelve@v1", "octo/sixteen@v2", "octo/twenty-three@v3"),
            Map.of(
                "octo/twelve@v1", "runs:\n  using: node12\n", "octo/sixteen@v2", "runs:\n  using: \"node16\"\n",
                "octo/twenty-three@v3", "runs:\n  using: " + "node%d".formatted(ActionRuntimeCheck.REQUIRED_RUNTIME - 1)
                    + "\n"
            )
        );
        assertEquals(
            List.of(
                outdated(WORKFLOW + ":5", "octo/twelve@v1", "node12"),
                outdated(WORKFLOW + ":6", "octo/sixteen@v2", "node16"),
                outdated(WORKFLOW + ":7", "octo/twenty-three@v3", "node23")
            ),
            outdatedIn(check)
        );
    }

    @Test
    void passesTheRequiredRuntimeALaterOneDockerAndAComposite() {
        ActionRuntimeCheck check = workflow(
            "runtime-passing", steps(CURRENT_CHECKOUT + " # v7.0.1", "octo/later@v9", "octo/image@v1", "octo/both@v2"),
            Map.of(
                CURRENT_CHECKOUT, NODE24, "octo/later@v9", "runs:\n  using: node26\n",
                "octo/image@v1", "runs:\n  using: docker\n  image: Dockerfile\n",
                "octo/both@v2", composite(CURRENT_CHECKOUT, "./local", "docker://alpine:3.21")
            )
        );
        assertTrue(Verdicts.clean(check.findings()), () -> String.valueOf(check.findings()));
        assertEquals(4, check.actions());
    }

    @Test
    void refusesACompositeThatReachesADeprecatedRuntimeAtTheLineThatUsesIt() {
        ActionRuntimeCheck check = workflow(
            "runtime-nested", steps("octo/outer@v1"),
            Map.of(
                "octo/outer@v1", composite("octo/middle@v2"),
                "octo/middle@v2", composite(CURRENT_CHECKOUT, "actions/cache@v3"),
                CURRENT_CHECKOUT, NODE24, "actions/cache@v3", "runs:\n  using: node16\n"
            )
        );
        assertEquals(
            List.of(
                WORKFLOW + ":5: octo/outer@v1 - runs actions/cache@v3, which declares node16" + DEPRECATED
                    + "a release of octo/outer whose actions all declare node24 or later"
            ),
            outdatedIn(check)
        );
    }

    @Test
    void followsACompositeThatReachesItselfOnlyOnce() {
        ActionRuntimeCheck check = workflow(
            "runtime-cycle", steps("octo/ping@v1"),
            Map.of("octo/ping@v1", composite("octo/pong@v1"), "octo/pong@v1", composite("octo/ping@v1"))
        );
        assertTrue(Verdicts.clean(check.findings()), () -> String.valueOf(check.findings()));
        assertEquals(2, check.actions());
    }

    @Test
    void ignoresACommentedOutUseAndReadsAQuotedOne() {
        String yaml = steps("'" + CHECKOUT + "'", "\"octo/double@v1\"").replace(
            "    steps:\n", "    steps:\n      # - uses: octo/commented@v1\n"
        );
        ActionRuntimeCheck check = workflow(
            "runtime-quoted", yaml, Map.of(CHECKOUT, NODE20, "octo/double@v1", "runs:\n  using: node16\n")
        );
        assertEquals(
            List.of(
                outdated(WORKFLOW + ":6", CHECKOUT, "node20"), outdated(WORKFLOW + ":7", "octo/double@v1", "node16")
            ),
            outdatedIn(check)
        );
    }

    @Test
    void judgesTheActionsACompositeActionOfTheRepositoryUses() {
        String action = ".github/actions/build/action.yml";
        Path root = new GitFixture("runtime-composite").write(action, composite(CHECKOUT)).root();
        ActionRuntimeCheck check = check(root, Map.of(CHECKOUT, NODE20));
        assertEquals(List.of(outdated(action + ":4", CHECKOUT, "node20")), outdatedIn(check));
        assertEquals(1, check.scanned());
    }

    @Test
    void judgesALocalActionAtTheLineThatDeclaresItsRuntime() {
        Path root = new GitFixture("runtime-local")
            .write(".github/actions/js/action.yml", "name: js\nruns:\n  using: node20\n  main: index.js\n")
            .write(".github/actions/next/action.yaml", "runs:\n  using: node24\n")
            .write(".github/actions/image/action.yml", "runs:\n  using: docker\n  image: Dockerfile\n")
            .write(".github/actions/odd/action.yml", "runs:\n  using: python3\n")
            .write(".github/actions/none/action.yml", "name: none\n")
            .root();
        ActionRuntimeCheck check = check(root, Map.of());
        assertEquals(
            List.of(
                ".github/actions/js/action.yml:3: node20 - the runtime this action declares" + DEPRECATED
                    + "node24 or later"
            ),
            outdatedIn(check)
        );
        assertEquals(
            List.of(
                ".github/actions/none/action.yml: runs - the action declares no runs.using; use runs.using naming"
                    + " node24 or later, docker, or composite",
                ".github/actions/odd/action.yml:2: python3 - not a runtime GitHub runs actions on; use node24 or later,"
                    + " docker, or composite"
            ),
            unreadIn(check).stream().sorted().toList()
        );
        assertEquals(5, check.scanned());
    }

    @Test
    void readsAnActionInADirectoryOfItsRepository() {
        String action = "github/codeql-action/upload-sarif@" + COMMIT;
        RecordedMetadata source = new RecordedMetadata(Map.of(action, NODE20));
        ActionRuntimeCheck check = new ActionRuntimeCheck(
            new GitFixture("runtime-directory").write(WORKFLOW, steps(action)).root(), source
        );
        assertEquals(
            List.of(new ActionReference("github", "codeql-action", "upload-sarif", COMMIT)),
            source.asked()
        );
        assertEquals(
            List.of(
                WORKFLOW + ":5: " + action + " - declares node20" + DEPRECATED
                    + "a commit of github/codeql-action whose metadata declares node24 or later"
            ),
            outdatedIn(check)
        );
    }
}
