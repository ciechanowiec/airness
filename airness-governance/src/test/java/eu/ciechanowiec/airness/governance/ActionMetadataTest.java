package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * An action's metadata names its runtime in the {@code using} key written directly under {@code runs}, in a block
 * or a flow mapping, quoted or not, and a composite action's steps name the actions it uses.
 */
class ActionMetadataTest {

    private static final int DEPRECATED_NODE = 20;
    private static final int REQUIRED_NODE = 24;
    private static final int DISTANT_NODE = 100;

    private static Optional<Located<String>> runtime(String yaml) {
        return ActionMetadata.read(yaml).runtime();
    }

    private static Optional<String> runtimeValue(String yaml) {
        return runtime(yaml).map(Located::value);
    }

    @Test
    void readsTheRuntimeWithItsLine() {
        String yaml = """
            name: Checkout
            description: Checks out a repository
            runs:
              using: node20
              main: dist/index.js
            """;
        assertEquals(Optional.of(new Located<>(4, "node20")), runtime(yaml));
    }

    @Test
    void readsEveryQuotingAndCaseOfTheRuntime() {
        assertEquals(Optional.of("node24"), runtimeValue("runs:\n  using: 'node24'\n"));
        assertEquals(Optional.of("node26"), runtimeValue("runs:\n  using: \"node26\"\n"));
        assertEquals(Optional.of("node20"), runtimeValue("runs:\n  using: Node20\n"));
        assertEquals(Optional.of("docker"), runtimeValue("runs:\n    using: docker\n    image: Dockerfile\n"));
    }

    @Test
    void ignoresACommentBesideOrInsteadOfTheRuntime() {
        assertEquals(Optional.of("node24"), runtimeValue("runs: # how it runs\n  using: node24 # moved\n"));
        assertEquals(Optional.of("node24"), runtimeValue("runs:\n  # using: node16\n  using: node24\n"));
        assertEquals(Optional.of("node24"), runtimeValue("# runs:\n#   using: node16\nruns:\n  using: node24\n"));
    }

    @Test
    void readsTheRuntimeWhereverItStandsAmongTheKeysOfRuns() {
        String yaml = """
            runs:

              main: dist/index.js
              post: dist/cleanup.js
              using: node16
            """;
        assertEquals(Optional.of(new Located<>(5, "node16")), runtime(yaml));
    }

    @Test
    void readsTheRuntimeOfAFlowMapping() {
        assertEquals(Optional.of("node20"), runtimeValue("runs: { main: index.js, using: node20 }\n"));
        assertEquals(Optional.of("node20"), runtimeValue("runs: {using: 'node20'}\n"));
    }

    @Test
    void readsNoRuntimeOutsideTheRunsMapping() {
        String deeper = """
            runs:
              steps:
                - run: echo
                  using: node20
              using: composite
            """;
        assertEquals(Optional.of("composite"), runtimeValue(deeper));
        assertEquals(Optional.empty(), runtimeValue("name: x\nusing: node20\n"), "a top-level using");
        assertEquals(Optional.empty(), runtimeValue("runs:\n  main: index.js\nusing: node20\n"), "after runs");
        assertEquals(Optional.empty(), runtimeValue("name: x\n"), "no runs at all");
        assertEquals(Optional.empty(), runtimeValue("runs:\n"), "an empty runs");
        assertEquals(Optional.empty(), runtimeValue("runs: {main: index.js}\n"), "a flow mapping without it");
        assertEquals(Optional.empty(), runtimeValue("runsfor:\n  using: node20\n"), "a longer key");
    }

    @Test
    void readsTheActionsACompositeActionsStepsUse() {
        String yaml = """
            runs:
              using: composite
              steps:
                - uses: actions/cache@v4
                - run: echo
                  shell: bash
                - uses: ./local
            """;
        assertEquals(
            List.of(new Located<>(4, "actions/cache@v4"), new Located<>(7, "./local")),
            ActionMetadata.read(yaml).steps()
        );
    }

    @Test
    void readsTheNodeVersionOfANodeRuntimeOnly() {
        assertEquals(Optional.of(DEPRECATED_NODE), ActionMetadata.nodeVersion("node20"));
        assertEquals(Optional.of(REQUIRED_NODE), ActionMetadata.nodeVersion("node24"));
        assertEquals(Optional.of(DISTANT_NODE), ActionMetadata.nodeVersion("node100"));
        assertEquals(Optional.empty(), ActionMetadata.nodeVersion("docker"));
        assertEquals(Optional.empty(), ActionMetadata.nodeVersion("composite"));
        assertEquals(Optional.empty(), ActionMetadata.nodeVersion("node"));
        assertEquals(Optional.empty(), ActionMetadata.nodeVersion("node20x"));
    }
}
