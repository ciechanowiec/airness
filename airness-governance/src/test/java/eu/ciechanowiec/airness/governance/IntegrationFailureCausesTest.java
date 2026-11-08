package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Every integration case that expects a build to fail also names why it failed.
 *
 * <p>An exit status alone proves only that something went wrong. A case written to prove that one gate refuses one
 * input would still pass when the build fails for any other reason, such as another preflight, a crashed scanner or
 * an unreachable Docker daemon, so the regression it exists to catch would go unnoticed. A case function that asserts
 * a nonzero exit of an execution therefore also asserts, in the same function, a line of that execution's own output:
 * a pattern match, a count, an order or a finding. A function is the unit because the suite writes one case as one
 * function, and a cause asserted in another function may describe another execution of the same name.
 */
class IntegrationFailureCausesTest {

    private static final String CASES = "airness-it/cases/";

    private static final Pattern FUNCTION = Pattern.compile("(?m)^([a-z_]+)\\(\\) \\{$");

    private static final Pattern FUNCTION_START = Pattern.compile("(?m)^(?=[a-z_]+\\(\\) \\{$)");

    private static final Pattern FAILURE = Pattern.compile(
        "(?m)^[ \\t]*expect_exit[ \\t]+(\\S+)[ \\t].*[ \\t](\\S+)[ \\t]*$"
    );

    /**
     * The assertions that read the output of the execution named first, as opposed to its status or a file.
     */
    private static final Pattern CAUSE = Pattern.compile(
        "(?m)^[ \\t]*(?:expect_match|expect_count|expect_before|expect_finding|expect_browser_line)[ \\t]+(\\S+)"
    );

    @Test
    void namesTheCauseOfEveryFailureTheSuiteExpects() {
        Path root = SelfModules.repository();
        List<Path> cases = Repository.trackedFiles(root).stream()
            .filter(file -> relative(root, file).startsWith(CASES) && file.toString().endsWith(".sh"))
            .toList();
        List<String> unexplained = new ArrayList<>();
        long failures = 0;
        for (Path file : cases) {
            String text = Repository.readText(file).orElse("");
            failures += FAILURE.matcher(joined(text)).results().filter(result -> !"0".equals(result.group(2))).count();
            unexplained.addAll(unexplained(relative(root, file), text));
        }
        assertTrue(failures > 0, "a guard that found no expected failure in the suite read nothing and proves nothing");
        assertEquals(
            List.of(), unexplained,
            "assert a line of the failing execution's output, for example with expect_match, beside its exit status"
        );
    }

    @Test
    void reportsAFailureAssertedByItsStatusAlone() {
        String suite = """
            run_bare_case() {
                run_maven bare maven "${consumer}" validate
                expect_exit bare 'bare: the gate refuses' 1
            }
            """;
        assertEquals(List.of("cases.sh: run_bare_case: bare"), unexplained("cases.sh", suite));
    }

    @Test
    void acceptsAFailureWhoseOutputIsAsserted() {
        String suite = """
            run_named_case() {
                run_maven named maven "${consumer}" validate
                expect_exit named \\
                    'named: the gate refuses' 1
                expect_match named 'named: the refusal is named' 'ignored build input'
                expect_exit passing 'passing: a clean control passes' 0
            }
            """;
        assertEquals(List.of(), unexplained("cases.sh", suite));
    }

    @Test
    void readsTheCauseOnlyInsideTheFunctionThatExpectsTheFailure() {
        String suite = """
            run_first_case() {
                expect_exit shared 'first: the gate refuses' 1
            }

            run_second_case() {
                expect_match shared 'second: another run of the same name' 'refused'
            }
            """;
        assertEquals(List.of("cases.sh: run_first_case: shared"), unexplained("cases.sh", suite));
    }

    @Test
    void doesNotTakeAnAssertionOnAFileForTheCause() {
        String suite = """
            run_file_case() {
                expect_exit written 'written: the gate refuses' 1
                expect_file_match written 'written: a file holds the word' 'refused'
            }
            """;
        assertEquals(List.of("cases.sh: run_file_case: written"), unexplained("cases.sh", suite));
    }

    private static List<String> unexplained(String path, String text) {
        return FUNCTION_START.splitAsStream(joined(text))
            .flatMap(function -> unexplainedIn(path, function).stream())
            .toList();
    }

    private static List<String> unexplainedIn(String path, String function) {
        Matcher declaration = FUNCTION.matcher(function);
        if (!declaration.lookingAt()) {
            return List.of();
        }
        Set<String> causes = CAUSE.matcher(function).results()
            .map(result -> result.group(1))
            .collect(Collectors.toSet());
        return FAILURE.matcher(function).results()
            .filter(result -> !"0".equals(result.group(2)) && !causes.contains(result.group(1)))
            .map(result -> path + ": " + declaration.group(1) + ": " + result.group(1))
            .toList();
    }

    private static String joined(String text) {
        return text.replace("\\\n", " ");
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }
}
