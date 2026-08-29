package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpringConfigurationRulesTest {

    private static List<String> offences(String content) {
        return SpringConfigurationRules.offences(
            "application.yml", new SpringConfiguration("application.yml", content)
        );
    }

    @Test
    void reportsAnActuatorExposedAtAWildcard() {
        String configuration = """
            management:
              endpoints:
                web:
                  exposure:
                    include: "*"
            """;

        List<String> reported = offences(configuration);

        assertEquals(1, reported.size(), "the wildcard publishes every endpoint");
        assertTrue(reported.getFirst().contains("configprops"), "the offence names what it publishes");
    }

    @Test
    void acceptsAnActuatorThatNamesItsEndpoints() {
        String configuration = """
            management:
              endpoints:
                web:
                  exposure:
                    include: health,info
            """;

        List<String> reported = offences(configuration);

        assertEquals(List.of(), reported, "the endpoints are named");
    }

    @Test
    void reportsAStackTraceReturnedToTheCaller() {
        String configuration = """
            server:
              error:
                include-stacktrace: always
              shutdown: graceful
            """;

        List<String> reported = offences(configuration);

        assertEquals(1, reported.size(), "the trace reaches whoever asked");
    }

    @Test
    void reportsASchemaHibernateMayAlter() {
        String configuration = """
            spring:
              jpa:
                open-in-view: false
                hibernate:
                  ddl-auto: update
            """;

        List<String> reported = offences(configuration);

        assertEquals(1, reported.size(), "update lets Hibernate alter the schema");
        assertTrue(reported.getFirst().contains("ddl-auto"), "the offence names the key");
    }

    @Test
    void reportsAPersistenceSubsystemThatOmitsTheKeyEntirely() {
        String configuration = """
            spring:
              jpa:
                show-sql: false
            """;

        List<String> reported = offences(configuration);

        assertEquals(2, reported.size(), "both ddl-auto and open-in-view are missing");
    }

    @Test
    void leavesASubsystemThatIsNotConfiguredAlone() {
        String configuration = """
            spring:
              application:
                name: example
            """;

        List<String> reported = offences(configuration);

        assertEquals(List.of(), reported, "this project configures no persistence");
    }

    @Test
    void acceptsAPersistenceSubsystemThatAnswersBothKeys() {
        String configuration = """
            spring:
              jpa:
                open-in-view: false
                hibernate:
                  ddl-auto: validate
            """;

        List<String> reported = offences(configuration);

        assertEquals(List.of(), reported, "both are declared as they have to be");
    }

    @Test
    void reportsAPoolWithNoBounds() {
        String configuration = """
            spring:
              datasource:
                url: jdbc:postgresql://localhost/example
            """;

        List<String> reported = offences(configuration);

        assertTrue(reported.size() >= 2, "max-lifetime and leak detection are both missing");
    }

    @Test
    void reportsALiteralSecret() {
        String configuration = """
            spring:
              datasource:
                credential: written-into-the-file
            """;

        List<String> reported = offences(configuration);

        assertTrue(
            reported.stream().anyMatch(offence -> offence.contains("literal secret")),
            "the value ships inside the artifact"
        );
    }

    @Test
    void acceptsASecretReadFromAPlaceholder() {
        String configuration = """
            spring:
              datasource:
                password: ${DB_PASSWORD}
            """;

        List<String> reported = offences(configuration);

        assertEquals(
            List.of(), reported.stream().filter(offence -> offence.contains("literal secret")).toList(),
            "the value comes from the environment"
        );
    }

    @Test
    void reportsAKeyThatIsNotWrittenInKebabCase() {
        String configuration = """
            spring:
              jpa:
                openInView: false
                hibernate:
                  ddl-auto: validate
            """;

        List<String> reported = offences(configuration);

        assertEquals(1, reported.size(), "two spellings of one key can otherwise sit here");
        assertTrue(reported.getFirst().contains("kebab-case"), "the offence says which convention");
    }

    @Test
    void reportsABakedInProfile() {
        String configuration = """
            spring:
              profiles:
                active: production
            """;

        List<String> reported = offences(configuration);

        assertTrue(
            reported.stream().anyMatch(offence -> offence.contains("promoted")),
            "the artifact chooses its own environment"
        );
    }

    @Test
    void acceptsASecretKeyLeftDeliberatelyEmpty() {
        String configuration = """
            spring:
              datasource:
                credential:
            """;

        List<String> reported = offences(configuration);

        assertEquals(List.of(), reported, "an empty value carries nothing to ship");
    }

    @Test
    void acceptsAValueWrittenInQuotationMarks() {
        String configuration = """
            server:
              shutdown: "graceful"
            """;

        List<String> reported = offences(configuration);

        assertEquals(List.of(), reported, "the quotation marks are not part of the value");
    }

    // Every value rule is written as a predicate over a value that is present. A configuration that
    // declares all of them correctly is what exercises the other side of each one.
    @Test
    void acceptsAConfigurationThatAnswersEveryRuleCorrectly() {
        String configuration = """
            management:
              endpoints:
                web:
                  exposure:
                    include: health,info
              endpoint:
                health:
                  show-details: when-authorized
            server:
              shutdown: graceful
              error:
                include-stacktrace: never
                include-message: never
            spring:
              main:
                allow-circular-references: false
                allow-bean-definition-overriding: false
              h2:
                console:
                  enabled: false
              jpa:
                show-sql: false
                open-in-view: false
                hibernate:
                  ddl-auto: none
            """;

        List<String> reported = offences(configuration);

        assertEquals(List.of(), reported, "each rule is satisfied rather than absent");
    }

    @Test
    void acceptsAPoolThatDeclaresItsBounds() {
        String configuration = """
            spring:
              datasource:
                url: jdbc:postgresql://localhost/example
                hikari:
                  max-lifetime: 1200000
                  leak-detection-threshold: 60000
            """;

        List<String> reported = offences(configuration);

        assertEquals(List.of(), reported, "both bounds are written");
    }
}
