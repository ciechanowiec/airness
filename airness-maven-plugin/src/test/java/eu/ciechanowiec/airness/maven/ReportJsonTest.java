package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * What a tool printed is read as a JSON document only when it is one, and a refusal quotes what was printed, cut
 * short when it runs long.
 */
class ReportJsonTest {

    private static final String LONG_PRINTOUT = "Unable to find image locally " + "x".repeat(300);

    @Test
    void readsAnObjectOrAnArrayATooPrinted() {
        assertAll(
            () -> assertEquals(
                "CycloneDX", ReportJson.parse("{\"bomFormat\": \"CycloneDX\"}").path("bomFormat").asText()
            ),
            () -> assertEquals(2, ReportJson.parse("[\"node@sha256:a\", \"node@sha256:b\"]").size())
        );
    }

    @Test
    void refusesTextThatIsNoDocumentQuotingWhatWasPrinted() {
        IllegalStateException empty = assertThrows(IllegalStateException.class, () -> ReportJson.parse(""));
        IllegalStateException scalar = assertThrows(IllegalStateException.class, () -> ReportJson.parse("42"));
        IllegalStateException malformed = assertThrows(
            IllegalStateException.class, () -> ReportJson.parse(LONG_PRINTOUT)
        );
        String quoted = Objects.requireNonNull(malformed.getMessage());
        assertAll(
            () -> assertTrue(Objects.requireNonNull(empty.getMessage()).contains("no JSON document")),
            () -> assertTrue(Objects.requireNonNull(scalar.getMessage()).endsWith("no JSON document: 42")),
            () -> assertTrue(quoted.contains("malformed JSON: Unable to find image locally"), quoted),
            () -> assertTrue(quoted.endsWith("..."), "a long printout is cut short")
        );
    }
}
