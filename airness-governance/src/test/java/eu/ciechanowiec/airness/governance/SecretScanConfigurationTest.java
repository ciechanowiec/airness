package eu.ciechanowiec.airness.governance;

import static eu.ciechanowiec.airness.governance.SecretScanFixtures.SHARED;
import static eu.ciechanowiec.airness.governance.SecretScanFixtures.broken;
import static eu.ciechanowiec.airness.governance.SecretScanFixtures.reported;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The secret scanner's configuration answers for the ways it could be used to switch the scan off.
 *
 * <p>How each exception's value is judged is covered by {@link SecretScanValueTest}.
 */
class SecretScanConfigurationTest {

    @Test
    void acceptsAnExceptionScopedToOneRuleAndOneExactValue() {
        String content = SHARED + """

            [[allowlists]]
            description = "Known invalid AWS value used to verify artifact secret detection"
            targetRules = ["generic-api-key"]
            regexTarget = "secret"
            regexes = [
                '''^AKIA1234567890123456$''',
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
    void rejectsATableHeaderWhoseBracketsDoNotBalance() {
        String content = SHARED + """

            [[allowlists]
            description = "a fixture"
            """;
        assertTrue(
            reported(content, "[[allowlists]"),
            "a header nobody can read is the line this reader refuses rather than waves through"
        );
    }

    @Test
    void rejectsAnExceptionWhoseRuleListIsEmpty() {
        String content = SHARED + """

            [[allowlists]]
            description = "a fixture"
            targetRules = []
            regexes = ['''^fixture-value-one$''']
            """;
        assertTrue(
            reported(content, "targetRules names no rule"),
            "gitleaks applies an exception with an empty rule list to every rule"
        );
    }

    @Test
    void rejectsAnExceptionWhoseRuleListHoldsOnlyABlankName() {
        String content = SHARED + """

            [[allowlists]]
            description = "a fixture"
            targetRules = [""]
            regexes = ['''^fixture-value-one$''']
            """;
        assertTrue(reported(content, "targetRules names no rule"), "a blank name scopes the exception to nothing");
    }

    @Test
    void rejectsAnExceptionMatchedAgainstMoreThanTheSecret() {
        for (String target : List.of("line", "match")) {
            String content = SHARED + """

                [[allowlists]]
                description = "a fixture"
                targetRules = ["generic-api-key"]
                regexTarget = "%s"
                regexes = ['''^fixture-value-one$''']
                """.formatted(target);
            assertTrue(
                reported(content, "matches the value against more than the secret"),
                "an anchored value matched against the " + target + " still excuses whatever shares it"
            );
        }
    }

    @Test
    void acceptsAnExceptionThatDeclaresTheSecretAsItsTarget() {
        for (String quote : List.of("\"", "'")) {
            String content = SHARED + """

                [[allowlists]]
                description = "a fixture"
                targetRules = ["generic-api-key"]
                regexTarget = %ssecret%s
                regexes = ['''^fixture-value-one$''']
                """.formatted(quote, quote);
            assertEquals(List.of(), broken(content), "the secret is the target gitleaks uses when none is declared");
        }
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
                '''^AKIA1234567890123456$''',
                '''^ghp_000000000000000000000000000000000000$''',
            ]
            """;
        assertEquals(
            List.of(), broken(content),
            "an array written one value to a line is one value per line rather than one unreadable line each"
        );
    }

    @Test
    void rejectsAKeyItDoesNotReadInItsTable() {
        String exception = """

            [[allowlists]]
            description = "a fixture"
            targetRules = ["generic-api-key"]
            regexes = ['''^fixture-value-one$''']
            """;
        Map<String, String> cases = Map.of(
            "enabled = false\n" + SHARED, "enabled is not a key this reader knows in the top level",
            SHARED + "path = \"loose.toml\"\n", "path is not a key this reader knows in [extend]",
            SHARED + exception + "condition = \"AND\"\n", "condition is not a key this reader knows in [allowlists]"
        );
        cases.forEach(
            (content, message) -> assertTrue(
                reported(content, message),
                "a key this reader passed over could change what gitleaks excuses without anyone seeing it"
            )
        );
    }

    @Test
    void rejectsATableItDoesNotKnow() {
        String content = SHARED + """

            [other]
            excuses = "everything"
            """;
        assertTrue(
            reported(content, "[other] is not a table this reader knows"), "an unknown table is unaccounted for"
        );
    }
}
