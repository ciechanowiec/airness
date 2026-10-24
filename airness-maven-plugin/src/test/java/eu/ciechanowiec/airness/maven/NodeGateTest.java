package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A gate binding names a plain id and a command, and runs as pnpm exec with the result file appended.
 */
class NodeGateTest {

    private static final Path RESULTS = Path.of("target/airness/node/ui");

    @Test
    void runsTheCommandThroughPnpmExecWithTheResultFileAppended() {
        NodeGate gate = NodeGate.of("eslint", "  airness-node   gate eslint ");
        assertEquals(RESULTS.resolve("eslint.json"), gate.result(RESULTS));
        assertEquals(RESULTS.resolve("gate-eslint.log"), gate.log(RESULTS));
        assertEquals(
            List.of("exec", "airness-node", "gate", "eslint", "--result", RESULTS.resolve("eslint.json").toString()),
            gate.arguments(gate.result(RESULTS))
        );
    }

    @Test
    void refusesAnIdThatCannotNameAResultFileAndACommandThatIsBlank() {
        assertThrows(IllegalArgumentException.class, () -> NodeGate.of("../eslint", "airness-node gate eslint"));
        assertThrows(IllegalArgumentException.class, () -> NodeGate.of("Eslint", "airness-node gate eslint"));
        assertThrows(IllegalArgumentException.class, () -> NodeGate.of("eslint", "   "));
    }
}
