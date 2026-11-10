package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The suppression ceiling counts one entry per suppressed rule rather than per annotation, scales its
 * ceiling with the code it read, and lists the offenders only once the ceiling is passed.
 */
class JavaSuppressionCeilingCheckTest {

    private static final List<Path> MAIN = List.of(Path.of("src/main/java"));
    private static final String SOURCE = "src/main/java/sample/Subject.java";
    private static final String CEILING = "ceiling";

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

    private static final String SIX_RULES_IN_ONE_ANNOTATION = """
        package sample;

        class Subject {

            @SuppressWarnings({"unchecked", "rawtypes", "deprecation", "removal", "serial", "cast"})
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
        Path root = new GitFixture("ceiling-count").write(SOURCE, TWO_RULES_IN_ONE_ANNOTATION).root();
        assertEquals(
            2,
            new JavaSuppressionCeilingCheck(root, MAIN, List.of()).count(),
            "one annotation naming two rules sets aside two rules"
        );
    }

    @Test
    void countsTheSpotBugsAnnotationWithoutCountingItsReason() {
        Path root = new GitFixture("ceiling-spotbugs").write(SOURCE, SPOTBUGS_WITH_A_REASON).root();
        assertEquals(
            1,
            new JavaSuppressionCeilingCheck(root, MAIN, List.of()).count(),
            "the reason beside a SpotBugs suppression is prose about it rather than a second rule"
        );
    }

    @Test
    void holdsASmallestCeilingUnderTheRate() {
        Path root = new GitFixture("ceiling-floor").write(SOURCE, TWO_RULES_IN_ONE_ANNOTATION).root();
        JavaSuppressionCeilingCheck check = new JavaSuppressionCeilingCheck(root, MAIN, List.of());
        assertEquals(5, check.ceiling(), "the rate alone would leave a small repository a ceiling of none");
        assertTrue(Verdicts.clean(check.findings()), "and two entries sit under it");
    }

    @Test
    void namesEveryOffenderOnlyOnceTheCeilingIsPassed() {
        Path root = new GitFixture("ceiling-listing").write(SOURCE, SIX_RULES_IN_ONE_ANNOTATION).root();
        List<String> offences = Verdicts.offences(
            new JavaSuppressionCeilingCheck(root, MAIN, List.of()).findings(), CEILING
        );
        assertEquals(6, offences.size(), "six entries pass the smallest ceiling of five, so each is an offence");
        assertTrue(offences.getFirst().contains("unchecked"), "the report names the rule that was set aside");
        assertTrue(offences.getFirst().contains("Subject.java"), "and the file it was set aside in");
    }

    @Test
    void countsNothingInASourceThatSuppressesNothing() {
        Path root = new GitFixture("ceiling-plain").write(SOURCE, PLAIN).root();
        JavaSuppressionCeilingCheck check = new JavaSuppressionCeilingCheck(root, MAIN, List.of());
        assertEquals(0, check.count(), "a source without an annotation sets nothing aside");
        assertEquals(1, check.scanned(), "though the source was read");
    }

    @Test
    void countsEachCoverageExclusionBesideTheAnnotations() {
        Path root = new GitFixture("ceiling-exclusions").write(SOURCE, TWO_RULES_IN_ONE_ANNOTATION).root();
        List<String> exclusions = List.of(
            "pom.xml: coverage exclusion sample.Application", "pom.xml: coverage exclusion sample.cli.*Command",
            "web/pom.xml: coverage exclusion sample.web.Launcher", "web/pom.xml: coverage exclusion sample.web.Shell"
        );
        JavaSuppressionCeilingCheck check = new JavaSuppressionCeilingCheck(root, MAIN, exclusions);
        List<String> offences = Verdicts.offences(check.findings(), CEILING);
        assertEquals(6, check.count(), "two annotated rules and four excluded classes are six exceptions");
        assertTrue(
            offences.contains("web/pom.xml: coverage exclusion sample.web.Shell"),
            () -> "an exclusion is named among the offences once the ceiling is passed, but named " + offences
        );
    }

    @Test
    void readsNoSourceWithoutASourceRoot() {
        Path root = new GitFixture("ceiling-rootless").write(SOURCE, PLAIN).root();
        assertEquals(
            0, new JavaSuppressionCeilingCheck(root, List.of(), List.of()).scanned(), "no root names the source"
        );
    }
}
