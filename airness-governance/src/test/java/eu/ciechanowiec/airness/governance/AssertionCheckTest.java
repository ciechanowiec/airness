package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The assertion check reads the test sources under the roots it is given, counts a test proven when an
 * assertion is reachable from it, and reports an assertion over literals alone separately.
 */
class AssertionCheckTest {

    private static final List<Path> TESTS = List.of(Path.of("src/test/java"));
    private static final String SOURCE = "src/test/java/sample/SubjectTest.java";
    private static final String UNPROVEN = "reaches no assertion";
    private static final String SETTLED = "literals alone";

    private static final String PROVEN = """
        package sample;

        import static org.junit.jupiter.api.Assertions.assertEquals;

        import org.junit.jupiter.api.Test;

        class SubjectTest {

            @Test
            void addsTheTwoNumbersGiven() {
                assertEquals(4, new Subject().sum(2, 2));
            }
        }
        """;

    private static final String QUOTING_AN_ASSERTION = """
        package sample;

        import static org.junit.jupiter.api.Assertions.assertTrue;

        import org.junit.jupiter.api.Test;

        class SubjectTest {

            @Test
            void namesAnAssertionWithoutMakingOne() {
                assertTrue(new Subject().render().contains("assertEquals(1, 1)"), "the text is the subject");
            }
        }
        """;

    private static final String DRIVEN_ONLY = """
        package sample;

        import org.junit.jupiter.api.Test;

        class SubjectTest {

            @Test
            void addsTheTwoNumbersGiven() {
                new Subject().sum(2, 2);
            }
        }
        """;

    private static final String PROVEN_THROUGH_A_HELPER = """
        package sample;

        import static org.junit.jupiter.api.Assertions.assertEquals;

        import org.junit.jupiter.api.Test;

        class SubjectTest {

            @Test
            void addsTheTwoNumbersGiven() {
                thenTheSumIs(4);
            }

            private void thenTheSumIs(int expected) {
                assertEquals(expected, new Subject().sum(2, 2));
            }
        }
        """;

    private static final String COMPARING_TWO_CONSTANTS = """
        package sample;

        import static org.junit.jupiter.api.Assertions.assertEquals;

        import org.junit.jupiter.api.Test;

        class SubjectTest {

            @Test
            void addsTheTwoNumbersGiven() {
                assertEquals(4, 4);
            }
        }
        """;

    private static final String MESSAGE_FIRST = """
        package sample;

        import static org.junit.jupiter.api.Assertions.assertEquals;

        import org.junit.jupiter.api.Test;

        class SubjectTest {

            @Test
            void addsTheTwoNumbersGiven() {
                assertEquals("the sum is stable", 4, new Subject().sum(2, 2));
            }
        }
        """;

    @Test
    void passesOverATestThatAssertsWhatItProduced() {
        Path root = new GitFixture("assertion-proven").write(SOURCE, PROVEN).root();
        assertTrue(
            Verdicts.clean(new AssertionCheck(root, TESTS).findings()),
            "an assertion over a produced value breaks neither rule"
        );
    }

    @Test
    void reportsATestThatDrivesTheCodeWithoutJudgingIt() {
        Path root = new GitFixture("assertion-driven").write(SOURCE, DRIVEN_ONLY).root();
        List<Findings> findings = new AssertionCheck(root, TESTS).findings();
        assertEquals(1, Verdicts.offences(findings, UNPROVEN).size(), "the test asserts nothing at all");
        assertTrue(
            Verdicts.offences(findings, UNPROVEN).getFirst().contains("addsTheTwoNumbersGiven"),
            "and the report names it, because a line number alone does not say which test to open"
        );
    }

    @Test
    void followsACallIntoAHelperDeclaredBeside() {
        Path root = new GitFixture("assertion-helper").write(SOURCE, PROVEN_THROUGH_A_HELPER).root();
        assertTrue(
            Verdicts.clean(new AssertionCheck(root, TESTS).findings()),
            "an assertion a helper makes is still an assertion the test reaches"
        );
    }

    @Test
    void reportsAnAssertionWhoseOperandsAreBothWrittenOut() {
        Path root = new GitFixture("assertion-settled").write(SOURCE, COMPARING_TWO_CONSTANTS).root();
        List<Findings> findings = new AssertionCheck(root, TESTS).findings();
        assertEquals(0, Verdicts.offences(findings, UNPROVEN).size(), "an assertion was reached");
        assertEquals(1, Verdicts.offences(findings, SETTLED).size(), "but no change to the code can move it");
    }

    @Test
    void leavesALiteralMessageBesideAProducedValueAlone() {
        Path root = new GitFixture("assertion-message").write(SOURCE, MESSAGE_FIRST).root();
        assertEquals(
            0,
            Verdicts.offences(new AssertionCheck(root, TESTS).findings(), SETTLED).size(),
            "a message written before the operands is not a second operand"
        );
    }

    @Test
    void countsTheSourcesItRead() {
        Path root = new GitFixture("assertion-scope").write(SOURCE, PROVEN).root();
        assertEquals(1, new AssertionCheck(root, TESTS).scanned(), "one source lies under the root given");
    }

    @Test
    void reportsAnEmptyScopeRatherThanACleanTree() {
        Path root = new GitFixture("assertion-empty").write(SOURCE, DRIVEN_ONLY).root();
        AssertionCheck check = new AssertionCheck(root, List.of(Path.of("src/test/kotlin")));
        assertEquals(0, check.scanned(), "a root that names nothing read nothing");
        assertTrue(
            Verdicts.clean(check.findings()),
            "and so reports the same verdict as a clean tree, which is why the caller refuses a zero scope"
        );
    }

    @Test
    void readsAQuotedAssertionAsTextRatherThanAsOneItCouldReport() {
        Path root = new GitFixture("assertion-quoted").write(SOURCE, QUOTING_AN_ASSERTION).root();
        assertTrue(
            Verdicts.clean(new AssertionCheck(root, TESTS).findings()),
            "an assertion quoted inside a one-line literal is text, not an assertion this file makes"
        );
    }
}
