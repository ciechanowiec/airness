package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.CheckstyleConfigurationTest.Finding;
import eu.ciechanowiec.airness.governance.CheckstyleConfigurationTest.Fixture;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DetachedChainDotCheckTest {

    private static final String RULE = "AirnessDetachedChainDot";
    private static final String DETACHED = """
        class Sample {
            Object read() {
                return values.or(
                    () -> this.atTopLevel(RULES, identifier.toString())
                        .map(entry -> entry.get(declared.name()))
                )
                .map(JsonNode::asInt);
            }
        }
        """;
    private static final String GAP = ")\n        .map";

    @Test
    void rejectsTheDetachedDotAfterANestedLambda(@TempDir Path directory) {
        assertEquals(1, findings(directory, DETACHED));
    }

    @Test
    void rejectsSeveralBlankLinesAndHorizontalWhitespace(@TempDir Path directory) {
        String source = DETACHED.replace(GAP, ") \t\n\n \t\n\t.map");
        assertEquals(1, findings(directory, source));
    }

    @Test
    void rejectsWindowsAndClassicMacLineEndings(@TempDir Path directory) {
        assertEquals(1, findings(directory, DETACHED.replace("\n", "\r\n")));
        assertEquals(1, findings(directory, DETACHED.replace("\n", "\r")));
    }

    @Test
    void acceptsTheDotBesideTheClosingParenthesis(@TempDir Path directory) {
        assertEquals(0, findings(directory, DETACHED.replace(GAP, ").map")));
    }

    @Test
    void acceptsOrdinaryFluentChains(@TempDir Path directory) {
        String source = """
            class Sample {
                Object read() {
                    return values.stream()
                        .filter(Value::active)
                        .map(Value::name)
                        .toList();
                }
            }
            """;
        assertEquals(0, findings(directory, source));
    }

    @Test
    void preservesCommentsBetweenTheParenthesisAndDot(@TempDir Path directory) {
        assertEquals(0, findings(directory, DETACHED.replace(GAP, ") // explanation\n        .map")));
        assertEquals(0, findings(directory, DETACHED.replace(GAP, ")\n        /* explanation */ .map")));
        assertEquals(0, findings(directory, DETACHED.replace(GAP, ")\n        // explanation\n        .map")));
    }

    @Test
    void ignoresExamplesInsideCommentsAndLiterals(@TempDir Path directory) {
        String source = """
            class Sample {
                /*
                )
                .map(Value::name)
                */
                String example = \"""
                    )
                    .map(Value::name)
                    \""";
                String escaped = ")\\n.map(Value::name)";
                char closing = ')';
            }
            """;
        assertEquals(0, findings(directory, source));
    }

    @Test
    void reportsEveryDetachedDot(@TempDir Path directory) {
        String source = """
            class Sample {
                Object read() {
                    return first(
                        value
                    )
                    .second(
                        another
                    )
                    .result;
                }
            }
            """;
        assertEquals(2, findings(directory, source));
    }

    @Test
    void reportsTheDotAndItsRepairWithTheCompleteConfiguration(@TempDir Path directory) {
        Fixture fixture = new Fixture("Sample.java", DETACHED, RULE, 7);
        List<Finding> findings = CheckstyleConfigurationTest.inspect(directory, fixture, false).stream()
            .filter(finding -> finding.is(RULE))
            .toList();
        assertEquals(1, findings.size());
        assertTrue(findings.getFirst().matches(fixture));
        assertTrue(findings.getFirst().message().contains(").method(...)"));
    }

    private static int findings(Path directory, String source) {
        return CheckstyleRule.findings(directory, source, RULE);
    }
}
