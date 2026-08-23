package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The suppression budget counts one entry per suppressed rule rather than per annotation, scales its
 * ceiling with the code it read, and lists the offenders only once the ceiling is passed.
 */
class SuppressionBudgetCheckTest {

    private static final List<Path> MAIN = List.of(Path.of("src/main/java"));
    private static final String SOURCE = "src/main/java/sample/Subject.java";
    private static final String CEILING = "ceiling";
    private static final double GENEROUS = 2;
    private static final double NONE = 0;

    private static final String PLAIN = """
        package sample;

        class Subject {

            int value() {
                return 0;
            }
        }
        """;

    private static final String TWO_RULES_IN_ONE_ANNOTATION = """
        package sample;

        class Subject {

            @SuppressWarnings({"unchecked", "rawtypes"})
            int value() {
                return 0;
            }
        }
        """;

    private static final String SPOTBUGS_WITH_A_REASON = """
        package sample;

        import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

        class Subject {

            @SuppressFBWarnings(
                justification = "the caller owns the array, see value() for why",
                value = "EI_EXPOSE_REP"
            )
            int value() {
                return 0;
            }
        }
        """;

    @Test
    void countsOneEntryPerSuppressedRuleRatherThanPerAnnotation() {
        Path root = new GitFixture("budget-count").write(SOURCE, TWO_RULES_IN_ONE_ANNOTATION).root();
        assertEquals(
            2,
            new SuppressionBudgetCheck(root, MAIN, GENEROUS).count(),
            "one annotation naming two rules sets aside two rules"
        );
    }

    @Test
    void countsTheSpotBugsAnnotationWithoutCountingItsReason() {
        Path root = new GitFixture("budget-spotbugs").write(SOURCE, SPOTBUGS_WITH_A_REASON).root();
        assertEquals(
            1,
            new SuppressionBudgetCheck(root, MAIN, GENEROUS).count(),
            "the reason beside a SpotBugs suppression is prose about it rather than a second rule"
        );
    }

    @Test
    void holdsASmallestCeilingUnderTheDeclaredRate() {
        Path root = new GitFixture("budget-floor").write(SOURCE, TWO_RULES_IN_ONE_ANNOTATION).root();
        SuppressionBudgetCheck check = new SuppressionBudgetCheck(root, MAIN, GENEROUS);
        assertEquals(5, check.ceiling(), "the rate alone would leave a small repository a budget of none");
        assertTrue(Verdicts.clean(check.findings()), "and two entries sit under it");
    }

    @Test
    void switchesTheSmallestCeilingOffWhenTheRateIsZero() {
        Path root = new GitFixture("budget-zero").write(SOURCE, TWO_RULES_IN_ONE_ANNOTATION).root();
        SuppressionBudgetCheck check = new SuppressionBudgetCheck(root, MAIN, NONE);
        assertEquals(0, check.ceiling(), "a declared rate of zero is how a repository states it carries none");
        assertEquals(2, Verdicts.offences(check.findings(), CEILING).size(), "so both entries are offences");
    }

    @Test
    void namesEveryOffenderOnlyOnceTheCeilingIsPassed() {
        Path root = new GitFixture("budget-listing").write(SOURCE, TWO_RULES_IN_ONE_ANNOTATION).root();
        List<String> offences = Verdicts.offences(
            new SuppressionBudgetCheck(root, MAIN, NONE).findings(), CEILING
        );
        assertTrue(offences.getFirst().contains("unchecked"), "the report names the rule that was set aside");
        assertTrue(offences.getFirst().contains("Subject.java"), "and the file it was set aside in");
    }

    @Test
    void countsNothingInASourceThatSuppressesNothing() {
        Path root = new GitFixture("budget-plain").write(SOURCE, PLAIN).root();
        SuppressionBudgetCheck check = new SuppressionBudgetCheck(root, MAIN, NONE);
        assertEquals(0, check.count(), "a source without an annotation sets nothing aside");
        assertEquals(1, check.scanned(), "though the source was read");
    }
}
