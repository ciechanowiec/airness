package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A credential is accepted only where the environment supplies it. A placeholder whose default is a literal
 * reads the environment when the variable is set and runs with the literal when it is not, so the literal
 * ships inside the artifact exactly as a value written outright does.
 */
class SpringConfigurationSecretsTest {

    private static final String YAML = "application.yml";
    private static final String PROPERTIES = "application.properties";
    private static final String LITERAL = "literal secret";

    private static List<String> secrets(String file, String content) {
        return SpringConfigurationRules.offences(file, new SpringConfiguration(file, content)).stream()
            .filter(offence -> offence.contains(LITERAL))
            .toList();
    }

    private static List<String> password(String value) {
        return secrets(PROPERTIES, "spring.datasource.password=" + value + "\n");
    }

    @Test
    void acceptsAPlaceholderWithNoDefault() {
        assertEquals(List.of(), password("${DB_PASSWORD}"), "the value comes from the environment alone");
    }

    @Test
    void acceptsAPlaceholderWithAnEmptyDefault() {
        assertEquals(List.of(), password("${DB_PASSWORD:}"), "an empty default carries nothing to ship");
    }

    @Test
    void reportsAPlaceholderFallingBackToALiteral() {
        assertEquals(
            1, password("${DB_PASSWORD:written-here}").size(),
            "an unset variable runs the application with the literal the file ships"
        );
    }

    @Test
    void reportsAPlaceholderFallingBackToALiteralInsideDoubleQuotes() {
        assertEquals(
            1, password("\"${DB_PASSWORD:written-here}\"").size(),
            "the quotation marks are notation and leave the literal where it was"
        );
    }

    @Test
    void reportsAPlaceholderFallingBackToALiteralInsideSingleQuotes() {
        String configuration = """
            spring:
              datasource:
                password: '${DB_PASSWORD:written-here}'
            """;

        assertEquals(1, secrets(YAML, configuration).size(), "single quotation marks are notation as well");
    }

    @Test
    void acceptsAPlaceholderFallingBackToAnotherPlaceholder() {
        assertEquals(
            List.of(), password("${DB_PASSWORD:${FALLBACK_PASSWORD}}"),
            "the default reads the environment again"
        );
    }

    @Test
    void reportsANestedPlaceholderThatFallsBackToALiteralInTheEnd() {
        assertEquals(
            1, password("${DB_PASSWORD:${FALLBACK_PASSWORD:written-here}}").size(),
            "the literal is only one variable further away"
        );
    }

    @Test
    void acceptsAQuotedPlaceholderWithNoDefault() {
        String configuration = """
            spring:
              datasource:
                password: "${DB_PASSWORD}"
            """;

        assertEquals(List.of(), secrets(YAML, configuration), "the quotation marks change nothing");
    }

    @Test
    void acceptsASecretLeftEmptyInQuotationMarks() {
        assertEquals(List.of(), password("\"\""), "an empty string in quotation marks is still empty");
    }

    @Test
    void reportsALiteralWrittenBesideAPlaceholder() {
        assertEquals(
            1, password("prefix-${DB_PASSWORD}").size(),
            "the text outside the placeholder ships whatever the variable holds"
        );
    }

    @Test
    void reportsAPlaceholderThatIsNeverClosed() {
        assertEquals(1, password("${DB_PASSWORD").size(), "an unclosed placeholder is read literally");
    }

    @Test
    void acceptsTwoPlaceholdersWrittenSideBySide() {
        assertEquals(
            List.of(), password("${DB_USER_PASSWORD}${DB_PASSWORD_SUFFIX}"),
            "both halves come from the environment"
        );
    }
}
