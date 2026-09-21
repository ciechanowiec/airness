package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The accessibility check reads the test sources under the roots it is given and reports a project
 * that asks the rules its own question or locates them by a version of its own.
 */
class AxeAuditCheckTest {

    private static final List<Path> TESTS = List.of(Path.of("src/test/java"));

    private static final String SOURCE = "src/test/java/sample/PageAccessibilityTest.java";

    private static final String ASKED = "asks the accessibility rules its own question";

    private static final String LOCATED = "locates the accessibility rules by a version";

    private static final String OWN_QUESTION = """
        package sample;

        class PageAccessibilityTest {

            private static final String VIOLATIONS = ""\"
                const done = arguments[arguments.length - 1];
                axe.run(document, {resultTypes: ['violations']})
                   .then(report => done(report.violations));
                ""\";
        }
        """;

    private static final String OWN_VERSION = """
        package sample;

        class PageAccessibilityTest {

            private static final String RULES =
                "META-INF/resources/webjars/axe-core/4.13.0/axe.min.js";
        }
        """;

    private static final String THROUGH_THE_HARNESS = """
        package sample;

        import eu.ciechanowiec.airness.web.AxeAudit;
        import eu.ciechanowiec.airness.web.AxeLibrary;

        class PageAccessibilityTest {

            String rules() {
                return AxeLibrary.rules() + AxeAudit.SCRIPT;
            }
        }
        """;

    private static final String NAMED_IN_A_COMMENT = """
        package sample;

        class PageAccessibilityTest {

            // The harness runs axe.run(document) for us from webjars/axe-core, so this asks nothing.
            String rules() {
                return "";
            }
        }
        """;

    @Test
    void passesOverATestThatAsksThroughTheHarness() {
        Path root = new GitFixture("axe-harness").write(SOURCE, THROUGH_THE_HARNESS).root();
        assertTrue(
            Verdicts.clean(new AxeAuditCheck(root, TESTS).findings()),
            "a project that runs the shipped question breaks neither rule"
        );
    }

    @Test
    void reportsATestThatWritesTheQuestionItself() {
        Path root = new GitFixture("axe-own-question").write(SOURCE, OWN_QUESTION).root();
        List<String> offences = Verdicts.offences(new AxeAuditCheck(root, TESTS).findings(), ASKED);
        assertEquals(1, offences.size(), "the test invokes the rules on its own terms");
        assertTrue(
            offences.getFirst().startsWith("src/test/java/sample/PageAccessibilityTest.java: line 7"),
            "and the report names the file and the line it was written on"
        );
    }

    @Test
    void saysWhatToRunInsteadOfTheQuestionItReported() {
        Path root = new GitFixture("axe-repair").write(SOURCE, OWN_QUESTION).root();
        assertTrue(
            new AxeAuditCheck(root, TESTS).findings().getFirst().headline().contains("AxeAudit.SCRIPT"),
            "a message that named only the defect would leave the repair to be guessed"
        );
    }

    @Test
    void reportsATestThatNamesTheVersionOfTheRulesItself() {
        Path root = new GitFixture("axe-own-version").write(SOURCE, OWN_VERSION).root();
        List<String> offences = Verdicts.offences(new AxeAuditCheck(root, TESTS).findings(), LOCATED);
        assertEquals(1, offences.size(), "a version typed into a project is a second place to pin it");
    }

    @Test
    void readsNoQuestionOutOfAComment() {
        Path root = new GitFixture("axe-commented").write(SOURCE, NAMED_IN_A_COMMENT).root();
        assertTrue(
            Verdicts.clean(new AxeAuditCheck(root, TESTS).findings()),
            "prose about the rules is not a use of them"
        );
    }

    @Test
    void countsTheSourcesItRead() {
        Path root = new GitFixture("axe-scope").write(SOURCE, THROUGH_THE_HARNESS).root();
        assertEquals(1, new AxeAuditCheck(root, TESTS).scanned(), "one source lies under the root given");
    }

    @Test
    void readsNothingUnderARootThatNamesNoSource() {
        Path root = new GitFixture("axe-empty").write(SOURCE, OWN_QUESTION).root();
        AxeAuditCheck check = new AxeAuditCheck(root, List.of(Path.of("src/test/kotlin")));
        assertEquals(0, check.scanned(), "a root that names nothing read nothing");
        assertTrue(Verdicts.clean(check.findings()), "which is why the caller refuses a zero scope");
    }
}
