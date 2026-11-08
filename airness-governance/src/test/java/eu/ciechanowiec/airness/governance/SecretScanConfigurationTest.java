package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
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
    void saysThatAShortExceptionIsShortRatherThanThatItIsAPattern() {
        String content = SHARED + """

            [[allowlists]]
            description = "a fixture"
            targetRules = ["generic-api-key"]
            regexes = ['''^hunter2xyz$''']
            """;
        assertTrue(
            reported(content, "is too short to be an exact value"),
            "a literal carrying no metacharacter fails on its length, and saying otherwise sends the reader hunting"
        );
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
    void rejectsAnUnanchoredValueThatARealSecretCanContain() {
        String content = SHARED + """

            [[allowlists]]
            description = "a fixture"
            targetRules = ["private-key"]
            regexes = ['''PRIVATE KEY-----''']
            """;
        assertTrue(
            reported(content, "matches every finding that contains it"),
            "gitleaks searches the value anywhere, so this literal would excuse every private key in the history"
        );
    }

    @Test
    void acceptsAnAnchoredValueThatSpellsOrdinaryCharactersEscapesAndLineBreaks() {
        String content = SHARED + """

            [[allowlists]]
            description = "a fixture"
            targetRules = ["generic-api-key"]
            regexes = ['''^fixture value\\+one\\nsecond-line$''']
            """;
        assertEquals(
            List.of(), broken(content),
            "a whole fixture is entitled to a space, an escaped plus and a line break, and matches only itself"
        );
    }

    @Test
    void rejectsAnEscapedCharacterClassInsideTheAnchors() {
        String content = SHARED + """

            [[allowlists]]
            description = "a fixture"
            targetRules = ["generic-api-key"]
            regexes = ['''^fixture-\\d\\d\\d-value$''']
            """;
        assertTrue(
            reported(content, "is a pattern rather than an exact value"),
            "an escaped letter is a class, which matches more than the value it is written as"
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
    void rejectsARegexWrittenAsADoubleQuotedString() {
        List<String> values = List.of(
            "\"^fixture\\\\wvalue-one$\"", "\"\"\"^fixture\\\\wvalue-one$\"\"\"", "\"^fixture-value-one$\""
        );
        for (String value : values) {
            String content = SHARED + """

                [[allowlists]]
                description = "a fixture"
                targetRules = ["generic-api-key"]
                regexes = [%s]
                """.formatted(value);
            assertTrue(
                reported(content, "regexes holds a double-quoted string"),
                "gitleaks decodes " + value + " before compiling it, so the regex it runs is not the one written"
            );
        }
    }

    @Test
    void acceptsARegexWrittenAsEitherLiteralString() {
        for (String value : List.of("'''^fixture-value-one$'''", "'^fixture-value-one$'")) {
            String content = SHARED + """

                [[allowlists]]
                description = "a fixture"
                targetRules = ["generic-api-key"]
                regexes = [%s]
                """.formatted(value);
            assertEquals(List.of(), broken(content), "a literal string reaches gitleaks exactly as written");
        }
    }

    @Test
    void rejectsAnExceptionWrittenAsAnInlineTable() {
        List<String> tables = List.of(
            "allowlists = [{ description = \"a fixture\", targetRules = [\"generic-api-key\"], paths = ['''.*'''] }]",
            "allowlists = [\n{ description = \"a fixture\", targetRules = [\"generic-api-key\"] },\n]",
            "allowlist = { paths = ['''.*'''] }",
            "rules = [{ id = \"generic-api-key\", regex = '''nothing''' }]"
        );
        for (String table : tables) {
            String content = table + "\n" + SHARED;
            assertTrue(
                reported(content, "line the harness cannot read")
                    && reported(content, "line 1: " + table.replace('\n', ' ')),
                "gitleaks reads an inline table as the table it spells, so this reader must not take it for one value"
            );
        }
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
