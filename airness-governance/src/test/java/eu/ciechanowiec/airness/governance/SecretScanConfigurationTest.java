package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The secret scanner's configuration answers for the ways it could be used to switch the scan off.
 */
class SecretScanConfigurationTest {

    private static final String SHARED = """
        title = "Secret scan configuration"

        [extend]
        useDefault = true
        """;

    @Test
    void acceptsAnExceptionScopedToOneRuleAndOneExactValue() {
        String content = SHARED + """

            [[allowlists]]
            description = "Known invalid AWS value used to verify artifact secret detection"
            targetRules = ["generic-api-key"]
            regexTarget = "line"
            regexes = [
                '''AKIA1234567890123456''',
            ]
            """;
        assertEquals(
            List.of(), broken(content),
            "an invalid fixture that names its rule and its exact value is the exception this file is for"
        );
    }

    @Test
    void rejectsAConfigurationThatDropsTheSharedRules() {
        String content = """
            [extend]
            useDefault = false
            """;
        assertTrue(
            reported(content, "useDefault must be declared true"),
            "one line would otherwise leave the scan running, finding nothing, and passing"
        );
    }

    @Test
    void rejectsAConfigurationThatDeclaresNoDefaultsAtAll() {
        String content = """
            title = "Secret scan configuration"
            """;
        assertTrue(
            reported(content, "useDefault must be declared true"),
            "absent and false are the same answer to whether the shared rules run"
        );
    }

    @Test
    void rejectsAnExceptionThatNamesNoRule() {
        String content = SHARED + """

            [[allowlists]]
            description = "a fixture"
            regexes = ['''AKIA1234567890123456''']
            """;
        assertTrue(reported(content, "needs both targetRules"), "an unscoped exception excuses every rule");
    }

    @Test
    void rejectsAnExceptionThatSaysNothingAboutItself() {
        String content = SHARED + """

            [[allowlists]]
            targetRules = ["generic-api-key"]
            regexes = ['''AKIA1234567890123456''']
            """;
        assertTrue(reported(content, "and description"), "an exception nobody explained is one nobody can retire");
    }

    @Test
    void rejectsThePatternFormOfAnException() {
        String content = SHARED + """

            [[allowlists]]
            description = "a fixture"
            targetRules = ["generic-api-key"]
            regexes = ['''.*''']
            """;
        assertTrue(
            reported(content, "is a pattern rather than an exact value"), "a pattern excuses whatever it matches"
        );
    }

    @Test
    void rejectsAnExceptionThatExcusesAWholeFile() {
        String content = SHARED + """

            [[allowlists]]
            description = "a fixture"
            targetRules = ["generic-api-key"]
            paths = ['''src/test/resources/fixture.txt''']
            """;
        assertTrue(reported(content, "excuses a whole file"), "a path excuses every secret the file will ever hold");
    }

    @Test
    void rejectsTheUnscopedFormOfTheAllowlistTable() {
        String content = SHARED + """

            [allowlist]
            regexes = ['''AKIA1234567890123456''']
            """;
        assertTrue(reported(content, "use [[allowlists]]"), "the singular table is unscoped by construction");
    }

    @Test
    void rejectsAProjectRuleThatCouldShadowASharedOne() {
        String content = SHARED + """

            [[rules]]
            id = "generic-api-key"
            regex = '''nothing'''
            """;
        assertTrue(reported(content, "can shadow one the shared set declares"), "an id is not a project's to reuse");
    }

    @Test
    void rejectsALineItCannotAccountFor() {
        String content = SHARED + "redacted";
        assertTrue(
            reported(content, "line the harness cannot read") || reported(content, "redacted"),
            "a reader that skipped what it did not recognise would pass an unknown key in silence"
        );
    }

    @Test
    void readsAValueSpreadOverSeveralLinesAsOneValue() {
        String content = SHARED + """

            [[allowlists]]
            description = "a fixture"
            targetRules = ["generic-api-key"]
            regexes = [
                '''AKIA1234567890123456''',
                '''ghp_000000000000000000000000000000000000''',
            ]
            """;
        assertEquals(
            List.of(), broken(content),
            "an array written one value to a line is one value per line rather than one unreadable line each"
        );
    }

    private static boolean reported(String content, CharSequence fragment) {
        return String.join("\n", broken(content)).contains(fragment);
    }

    private static List<String> broken(String content) {
        return new SecretScanConfiguration(content).findings().stream()
            .filter(verdict -> !verdict.clean())
            .map(Findings::report)
            .toList();
    }
}
