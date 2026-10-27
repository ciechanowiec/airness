package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The node ceiling is two suppressions, and one more per five hundred non-blank lines of the project's own code,
 * and the offences are named only once it is passed.
 */
class NodeSuppressionBudgetTest {

    private static final int LINES = 500;

    private static NodeSuppressionBudget budgetFixture() {
        String lines = "const a = 1\n\n".repeat(LINES);
        Path root = new GitFixture("node-budget").write("web/src/long.ts", lines)
            .write("web/src/quiet.ts", "// eslint-disable-next-line no-console, no-alert\nconsole.log(1)\n")
            .write("web/README.md", "<!-- eslint-disable -->\n").root();
        return new NodeSuppressionBudget(
            root, List.of(
                root.resolve("web/src/long.ts"), root.resolve("web/src/quiet.ts"), root.resolve("web/README.md")
            ),
            List.of()
        );
    }

    @Test
    void earnsTwoAndOnePerFiveHundredNonBlankLines() {
        NodeSuppressionBudget check = budgetFixture();
        assertEquals(2, check.scanned(), "Markdown is no node source");
        assertEquals(3, check.ceiling(), "502 non-blank lines earn one beyond the two, blank lines earn nothing");
        assertEquals(2, check.count());
        assertTrue(check.findings().getFirst().clean());
    }

    @Test
    void namesEveryOffenderOnceTheCeilingIsPassed() {
        Path root = new GitFixture("node-budget-over").write(
            "web/src/loud.ts", "// @ts-ignore\n// @ts-ignore\n/* v8 ignore next */\nexport const a = 1\n"
        ).root();
        NodeSuppressionBudget check = new NodeSuppressionBudget(
            root, List.of(root.resolve("web/src/loud.ts")), List.of()
        );
        List<String> offences = check.findings().getFirst().offences();
        assertEquals(3, offences.size());
        assertTrue(offences.getFirst().startsWith("web/src/loud.ts: line 1"));
    }
}
