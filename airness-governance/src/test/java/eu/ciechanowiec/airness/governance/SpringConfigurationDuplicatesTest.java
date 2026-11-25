package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A key written twice is read against the document it sits in rather than against the file. One file may
 * hold several documents, and the same key in two of them is how a profile overrides a default, so the
 * rule that would be right over a file is wrong over the whole of one.
 */
class SpringConfigurationDuplicatesTest {

    private static final String FILE = "application.yml";

    private static List<String> offences(String content) {
        return SpringConfigurationRules.offences(FILE, new SpringConfiguration(FILE, content));
    }

    @Test
    void reportsAKeyDeclaredTwiceInOneDocument() {
        String configuration = """
            spring:
              application:
                name: first
            spring:
              application:
                name: second
            """;

        List<String> reported = offences(configuration);

        assertEquals(1, reported.size(), "Spring binds the last of them and reads the first by nothing");
        assertTrue(reported.getFirst().contains("more than once"), "the offence says which defect it found");
    }

    @Test
    void namesTheEarlierLineTheLaterOneOverrides() {
        String configuration = """
            spring:
              application:
                name: first
            spring:
              application:
                name: second
            """;

        assertTrue(
            offences(configuration).getFirst().contains("line 3"),
            "the line that is read by nothing is the one the reader has to be sent to"
        );
    }

    @Test
    void acceptsTheSameKeyInEachItemOfAList() {
        String configuration = """
            spring:
              cloud:
                gateway:
                  routes:
                    - id: orders
                      uri: http://orders:8080
                    - id: billing
                      uri: http://billing:8080
            """;

        assertEquals(
            List.of(), offences(configuration),
            "each item of a list holds its own keys, so two items naming one key write it once each"
        );
    }

    @Test
    void reportsAKeyDeclaredTwiceInOneItem() {
        String configuration = """
            routes:
              - id: orders
                uri: http://first:8080
                uri: http://second:8080
            """;

        assertTrue(
            offences(configuration).getFirst().contains("line 3"),
            "within one item a key written twice is still bound once, from its last line"
        );
    }

    @Test
    void acceptsAKeyRedeclaredInALaterDocument() {
        String configuration = """
            spring:
              application:
                name: default
            ---
            spring:
              application:
                name: overridden
            """;

        assertEquals(
            List.of(), offences(configuration),
            "a second document overriding the first is how a profile is written"
        );
    }
}
