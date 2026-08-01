package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The comment-prose check reads the sources under the roots it is given, reports each rule separately,
 * and reports a scope of zero rather than a clean tree when the roots name nothing.
 */
class CommentProseCheckTest {

    private static final List<Path> MAIN = List.of(Path.of("src/main/java"));
    private static final String SOURCE = "src/main/java/sample/Subject.java";

    private static final String CLEAN = """
        package sample;

        /** A type whose prose stays short. */
        class Subject {

            /**
             * Reads the value, leaving it as it was found.
             *
             * @return the value
             */
            int value() {
                return 0;
            }
        }
        """;

    private static final String BROKEN = """
        package sample;

        /** A type whose prose runs on; it takes two clauses to say so. */
        class Subject {

            /**
             * Reads the value.
             *
             * @return the value.
             */
            int value() {
                return 0;
            }
        }
        """;

    @Test
    void passesOverProseThatBreaksNeitherRule() {
        Path root = new GitFixture("comment-prose-clean").write(SOURCE, CLEAN).root();
        assertTrue(
            Verdicts.clean(new CommentProseCheck(root, MAIN).findings()),
            "a short comment and a fragment @return break neither rule"
        );
    }

    @Test
    void reportsTheSemicolonAndTheFullStopSeparately() {
        Path root = new GitFixture("comment-prose-broken").write(SOURCE, BROKEN).root();
        List<Findings> findings = new CommentProseCheck(root, MAIN).findings();
        assertEquals(1, Verdicts.offences(findings, "semicolon").size(), "the joined clauses are one offence");
        assertEquals(1, Verdicts.offences(findings, "@return").size(), "the full stop is the other");
    }

    @Test
    void countsTheSourcesItRead() {
        Path root = new GitFixture("comment-prose-scope").write(SOURCE, CLEAN).root();
        assertEquals(1, new CommentProseCheck(root, MAIN).scanned(), "one source lies under the root given");
    }

    @Test
    void reportsAnEmptyScopeRatherThanACleanTree() {
        Path root = new GitFixture("comment-prose-empty").write(SOURCE, BROKEN).root();
        CommentProseCheck check = new CommentProseCheck(root, List.of(Path.of("src/main/kotlin")));
        assertEquals(0, check.scanned(), "a root that names nothing read nothing");
        assertTrue(
            Verdicts.clean(check.findings()),
            "and so reports the same verdict as a clean tree, which is why the caller refuses a zero scope"
        );
    }
}
