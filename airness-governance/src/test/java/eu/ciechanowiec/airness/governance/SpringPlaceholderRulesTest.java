package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A placeholder without a default fails startup wherever the key is missing, and a test boots with the
 * test profile's keys, so the base file is the one the key is read against.
 */
class SpringPlaceholderRulesTest {

    private static final SpringMetadata NOTHING = new SpringMetadata(List.of());
    private static final SpringMetadata MAIL = new SpringMetadata(
        List.of(
            new ConfigurationProperty(
                "spring.mail.host", "java.lang.String", false,
                new ConfigurationProperty.Deprecation("", "", "", "")
            )
        )
    );

    private static String reading(String annotation) {
        return """
            package sample;

            class Clock {

                Clock(%s String value) {
                }
            }
            """.formatted(annotation);
    }

    private static List<SpringConfiguration> base(String yaml) {
        return List.of(new SpringConfiguration("application.yml", yaml));
    }

    @Test
    void acceptsAKeyTheBaseFileDeclares() {
        List<String> offences = SpringPlaceholderRules.undeclaredPlaceholders(
            reading("@Value(\"${example.zone}\")"), base("example:\n  zone: UTC\n"), NOTHING
        );

        assertEquals(List.of(), offences, "the base file declares the key");
    }

    @Test
    void acceptsAKeyWrittenInAnotherSpelling() {
        List<String> offences = SpringPlaceholderRules.undeclaredPlaceholders(
            reading("@Value(\"${example.timeZone}\")"), base("example:\n  time-zone: UTC\n"), NOTHING
        );

        assertEquals(List.of(), offences, "Spring binds every spelling of one key");
    }

    @Test
    void acceptsAKeyTheClasspathBinds() {
        List<String> offences = SpringPlaceholderRules.undeclaredPlaceholders(
            reading("@Value(\"${spring.mail.host}\")"), base("example:\n  zone: UTC\n"), MAIL
        );

        assertEquals(List.of(), offences, "a dependency declares the key");
    }

    @Test
    void acceptsAPlaceholderCarryingADefault() {
        List<String> offences = SpringPlaceholderRules.undeclaredPlaceholders(
            reading("@Value(\"${example.zone:UTC}\")"), base("example:\n  other: 1\n"), NOTHING
        );

        assertEquals(List.of(), offences, "the placeholder answers for itself");
    }

    @Test
    void reportsAKeyNothingDeclares() {
        List<String> offences = SpringPlaceholderRules.undeclaredPlaceholders(
            reading("@Value(\"${example.zone}\")"), base("example:\n  other: 1\n"), NOTHING
        );

        assertEquals(1, offences.size(), "the key is read and declared nowhere the artifact ships");
        assertTrue(offences.getFirst().startsWith("line 5:"), "the offence points at the placeholder");
        assertTrue(offences.getFirst().contains("example.zone"), "the offence names the key");
    }

    @Test
    void readsEveryAnnotationCarryingAPlaceholder() {
        String source = """
            package sample;

            class Jobs {

                @Scheduled(cron = "${jobs.cron}", zone = "${jobs.zone}")
                void run() {
                    String text = "${not.a.key}";
                }
            }
            """;

        List<String> offences = SpringPlaceholderRules.undeclaredPlaceholders(
            source, base("jobs:\n  cron: '0 0 * * * *'\n"), NOTHING
        );

        assertEquals(1, offences.size(), "the zone is undeclared and the string in the body is no placeholder");
        assertTrue(offences.getFirst().contains("jobs.zone"), "the offence names the undeclared key");
    }
}
