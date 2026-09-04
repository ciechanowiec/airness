package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The settings that widen what a reply carries. Each of them is a value somebody wrote rather than a
 * default anything fell back to, so each is a decision, and what it decides is that a caller is handed
 * a credential, a copy of the process, or the session of whoever is reading.
 */
class SpringDisclosureConfigurationRulesTest {

    private static List<String> offences(String content) {
        return SpringConfigurationRules.offences(
            "application.yml", new SpringConfiguration("application.yml", content)
        );
    }

    @Test
    void reportsAnActuatorPublishingAnEndpointThatHandsOutASecret() {
        String configuration = """
            management:
              endpoints:
                web:
                  exposure:
                    include: health,env
            """;

        List<String> reported = offences(configuration);

        assertEquals(1, reported.size(), "an endpoint naming a credential is published here");
        assertTrue(reported.getFirst().contains("credential"), "the offence says what it hands out");
    }

    @Test
    void reportsAnActuatorPublishingAnEndpointThatStopsTheApplication() {
        String configuration = """
            management:
              endpoints:
                web:
                  exposure:
                    include: health, shutdown
            """;

        assertEquals(1, offences(configuration).size(), "a list is read item by item however it is spaced");
    }

    @Test
    void acceptsAnActuatorPublishingOnlyWhatItIsWatchedBy() {
        String configuration = """
            management:
              endpoints:
                web:
                  exposure:
                    include: health,info,metrics
            """;

        assertEquals(List.of(), offences(configuration), "health and metrics hand out nothing and change nothing");
    }

    @Test
    void reportsAnEnvironmentEndpointReturningItsValuesInFull() {
        String configuration = """
            management:
              endpoint:
                env:
                  show-values: always
            """;

        assertEquals(1, offences(configuration).size(), "a placeholder resolved a credential into one of them");
    }

    @Test
    void acceptsAnEnvironmentEndpointThatMasksItsValues() {
        String configuration = """
            management:
              endpoint:
                env:
                  show-values: when-authorized
            """;

        assertEquals(List.of(), offences(configuration), "a reader who signed in is a different caller");
    }

    @Test
    void reportsASessionCookieScriptCanRead() {
        String configuration = """
            server:
              shutdown: graceful
              servlet:
                session:
                  cookie:
                    http-only: false
            """;

        List<String> reported = offences(configuration);

        assertEquals(1, reported.size(), "anything injected into the page takes the session");
        assertTrue(reported.getFirst().contains("takes the session"), "the offence says what is lost");
    }

    @Test
    void acceptsASessionCookieScriptCannotRead() {
        String configuration = """
            server:
              shutdown: graceful
              servlet:
                session:
                  cookie:
                    http-only: true
            """;

        assertEquals(List.of(), offences(configuration), "the setting is what keeps the cookie out of the page");
    }
}
