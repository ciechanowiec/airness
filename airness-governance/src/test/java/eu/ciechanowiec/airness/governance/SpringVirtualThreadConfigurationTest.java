package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpringVirtualThreadConfigurationTest {

    private static List<String> offences(String content) {
        return SpringConfigurationRules.offences(
            "application.yml", new SpringConfiguration("application.yml", content)
        );
    }

    @Test
    void reportsVirtualThreadsTurnedOffInAProfileDocument() {
        String configuration = """
            spring:
              threads:
                virtual:
                  enabled: true
            ---
            spring:
              config:
                activate:
                  on-profile: production
              threads:
                virtual:
                  enabled: false
            """;

        List<String> reported = offences(configuration);

        assertEquals(1, reported.size(), "a profile turning virtual threads off is the setting it overrides");
        assertTrue(reported.getFirst().contains("spring.threads.virtual.enabled"), "the offence names the key");
    }

    @Test
    void reportsAReactiveWebApplicationType() {
        String configuration = """
            spring.main.web-application-type=reactive
            """;

        List<String> reported = SpringConfigurationRules.offences(
            "application.properties", new SpringConfiguration("application.properties", configuration)
        );

        assertEquals(1, reported.size(), "the reactive stack is refused");
        assertTrue(reported.getFirst().contains("servlet"), "the offence names the replacement");
    }

    @Test
    void acceptsAServletOrAnUnservedApplicationOnVirtualThreads() {
        String configuration = """
            spring:
              main:
                web-application-type: none
              threads:
                virtual:
                  enabled: true
            """;

        List<String> reported = offences(configuration);

        assertEquals(List.of(), reported, "an application on virtual threads that serves nothing is accepted");
    }
}
