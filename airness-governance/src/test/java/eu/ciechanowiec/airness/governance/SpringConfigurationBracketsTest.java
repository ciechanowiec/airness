package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A map key that carries dots of its own is written in brackets, and every spelling of that notation
 * has to read as the flattened key Spring binds it to.
 */
class SpringConfigurationBracketsTest {

    private static SpringConfiguration yaml(String content) {
        return new SpringConfiguration("application.yml", content);
    }

    @Test
    void readsABracketedMapKeyAsTheKeySpringBinds() {
        String content = """
            spring:
              mail:
                properties:
                  "[mail.smtp.timeout]": 5000
            """;

        SpringConfiguration read = yaml(content);

        assertEquals(
            "spring.mail.properties.mail.smtp.timeout", read.settings().getFirst().key(),
            "the brackets and the quotation marks are notation rather than name"
        );
    }

    @Test
    void readsASingleQuotedBracketedMapKey() {
        String content = """
            spring:
              mail:
                properties:
                  '[mail.smtp.timeout]': 5000
            """;

        SpringConfiguration read = yaml(content);

        assertTrue(read.declared("spring.mail.properties.mail.smtp.timeout").isPresent(), "either quote works");
    }

    @Test
    void keepsTheBracketedKeyAsTheProjectWroteIt() {
        String content = """
            spring:
              mail:
                properties:
                  "[mail.smtp.timeout]": 5000
            """;

        SpringConfiguration read = yaml(content);

        assertEquals("\"[mail.smtp.timeout]\"", read.settings().getFirst().raw(), "the written form is kept");
    }

    @Test
    void readsABracketedMapKeyInAPropertiesFile() {
        SpringConfiguration read = new SpringConfiguration(
            "application.properties", "spring.mail.properties[mail.smtp.timeout]=5000\n"
        );

        assertEquals(
            "5000", read.declared("spring.mail.properties.mail.smtp.timeout").orElseThrow().value(),
            "the bracket stands where a dot would"
        );
    }

    @Test
    void readsABracketedMapKeyWrittenAfterADotInAPropertiesFile() {
        SpringConfiguration read = new SpringConfiguration(
            "application.properties", "spring.mail.properties.[mail.smtp.timeout]=5000\n"
        );

        assertTrue(
            read.declared("spring.mail.properties.mail.smtp.timeout").isPresent(),
            "a dot before the bracket does not double the separator"
        );
    }
}
