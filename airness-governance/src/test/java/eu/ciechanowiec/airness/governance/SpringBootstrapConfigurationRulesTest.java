package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every repository query is parsed against the entity model when the repository is built, which is
 * what lets a test that boots the application prove the queries. A lazy bootstrap moves that parse to
 * the first call, and a query nothing calls in a test is then proven by nothing.
 */
class SpringBootstrapConfigurationRulesTest {

    private static List<String> offences(String content) {
        return SpringConfigurationRules.offences(
            "application.yml", new SpringConfiguration("application.yml", content)
        );
    }

    @Test
    void reportsRepositoriesBuiltOnFirstUse() {
        String configuration = """
            spring:
              data:
                jpa:
                  repositories:
                    bootstrap-mode: lazy
            """;

        List<String> reported = offences(configuration);

        assertEquals(1, reported.size(), "a query is then parsed on its first call rather than at startup");
        assertTrue(reported.getFirst().contains("first call"), "the offence says when the parse moves to");
    }

    @Test
    void acceptsRepositoriesBuiltAfterTheContextRefreshes() {
        String configuration = """
            spring:
              data:
                jpa:
                  repositories:
                    bootstrap-mode: deferred
            """;

        assertEquals(List.of(), offences(configuration), "a deferred bootstrap still parses every query at startup");
    }
}
