package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The settings a mail server asks for once one is named, and the notation they are accepted in.
 */
class SpringMailRulesTest {

    private static List<String> offences(String content) {
        return SpringConfigurationRules.offences(
            "application.yml", new SpringConfiguration("application.yml", content)
        );
    }

    @Test
    void reportsAMailServerWithNoTimeouts() {
        String configuration = """
            spring:
              mail:
                host: smtp.example.invalid
            """;

        List<String> reported = offences(configuration);

        assertEquals(3, reported.size(), "all three timeouts are missing");
        assertTrue(
            reported.stream().anyMatch(offence -> offence.contains("connectiontimeout"))
                && reported.stream().anyMatch(offence -> offence.contains("writetimeout")),
            "the wait to connect and the wait to write are both asked for"
        );
    }

    @Test
    void acceptsAMailServerThatDeclaresItsTimeouts() {
        String configuration = """
            spring:
              mail:
                host: smtp.example.invalid
                properties:
                  "[mail.smtp.connectiontimeout]": 5000
                  "[mail.smtp.timeout]": 5000
                  "[mail.smtp.writetimeout]": 5000
            """;

        List<String> reported = offences(configuration);

        assertEquals(List.of(), reported, "every timeout is written in the notation Spring documents");
    }
}
