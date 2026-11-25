package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.CheckstyleConfigurationTest.Finding;
import eu.ciechanowiec.airness.governance.CheckstyleConfigurationTest.Fixture;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoneChainReceiverCheckTest {

    private static final String RULE = "AirnessLoneChainReceiver";
    private static final String LONE = """
        class Sample {
            Object build() {
                return builder
                    .first(Order.FIRST)
                    .second(Order.SECOND)
                    .build();
            }
        }
        """;
    private static final String GAP = "builder\n            .first";

    @Test
    void rejectsAReceiverAloneAboveACallThatFitsBesideIt(@TempDir Path directory) {
        assertEquals(1, findings(directory, LONE));
    }

    @Test
    void rejectsAQualifiedReceiverAfterAnAssignmentAndInAnArgument(@TempDir Path directory) {
        String source = """
            class Sample {
                void build() {
                    Object built = this.builder
                        .first(Order.FIRST)
                        .build();
                    use(other,
                        source
                            .read());
                }
            }
            """;
        assertEquals(2, findings(directory, source));
    }

    @Test
    void acceptsTheFirstCallOnTheLineOfItsReceiver(@TempDir Path directory) {
        assertEquals(0, findings(directory, LONE.replace(GAP, "builder.first")));
    }

    @Test
    void acceptsAReceiverWhoseFirstCallDoesNotFitBesideIt(@TempDir Path directory) {
        String wide = "Order.FIRST, Order.SECOND, Order.THIRD, Order.FOURTH, Order.FIFTH, Order.SIXTH,"
            + " Order.SEVENTH, Order.LAST";
        assertEquals(0, findings(directory, LONE.replace("first(Order.FIRST)", "first(" + wide + ")")));
    }

    @Test
    void acceptsACallOrACommentEndingThePrecedingLine(@TempDir Path directory) {
        assertEquals(0, findings(directory, LONE.replace(GAP, "builder()\n            .first")));
        assertEquals(0, findings(directory, LONE.replace(GAP, "builder // the shared one\n            .first")));
        assertEquals(
            0, findings(directory, LONE.replace(GAP, "builder\n            // the shared one\n            .first"))
        );
    }

    @Test
    void ignoresExamplesInsideCommentsAndLiterals(@TempDir Path directory) {
        String source = """
            class Sample {
                /*
                return builder
                    .first(Order.FIRST)
                */
                String example = \"""
                    return builder
                        .first(Order.FIRST)
                    \""";
            }
            """;
        assertEquals(0, findings(directory, source));
    }

    @Test
    void reportsTheDotAndItsRepairWithTheCompleteConfiguration(@TempDir Path directory) {
        Fixture fixture = new Fixture("Sample.java", LONE, RULE, 4);
        List<Finding> findings = CheckstyleConfigurationTest.inspect(directory, fixture, false).stream()
            .filter(finding -> finding.is(RULE))
            .toList();
        assertEquals(1, findings.size());
        assertTrue(findings.getFirst().matches(fixture));
        assertTrue(findings.getFirst().message().contains("receiver.method(...)"));
    }

    private static int findings(Path directory, String source) {
        return CheckstyleRule.findings(directory, source, RULE);
    }
}
