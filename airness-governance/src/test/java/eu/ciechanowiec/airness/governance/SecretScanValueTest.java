package eu.ciechanowiec.airness.governance;

import static eu.ciechanowiec.airness.governance.SecretScanFixtures.SHARED;
import static eu.ciechanowiec.airness.governance.SecretScanFixtures.broken;
import static eu.ciechanowiec.airness.governance.SecretScanFixtures.reported;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * An exception in the secret scanner's configuration excuses one exact value, written as gitleaks reads it.
 */
class SecretScanValueTest {

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
}
