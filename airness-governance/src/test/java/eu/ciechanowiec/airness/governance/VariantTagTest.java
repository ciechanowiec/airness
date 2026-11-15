package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * A base-image tag reads as a version and its variant, and a newer release keeps the variant and the major
 * version.
 */
class VariantTagTest {

    private static VariantTag tag(String raw) {
        return VariantTag.from(raw).orElseThrow();
    }

    @Test
    void readsTheVersionAndTheVariantOfEachTagShape() {
        assertAll(
            () -> assertEquals(new VariantTag("26.8.2-alpine", "26.8.2", "-alpine"), tag("26.8.2-alpine")),
            () -> assertEquals(
                new VariantTag("25.0.5_8-jre-alpine-3.24", "25.0.5_8", "-jre-alpine-3.24"),
                tag("25.0.5_8-jre-alpine-3.24")
            ),
            () -> assertEquals(new VariantTag("3.24", "3.24", ""), tag("3.24")),
            () -> assertEquals(new VariantTag("v2.15.1", "v2.15.1", ""), tag("v2.15.1")),
            () -> assertEquals(Optional.empty(), VariantTag.from("bookworm")),
            () -> assertEquals(Optional.empty(), VariantTag.from("latest"))
        );
    }

    @Test
    void tellsAReleaseFromALineThatMoves() {
        assertAll(
            () -> assertTrue(tag("26.8.2-alpine").release()),
            () -> assertTrue(tag("25_8-jre").release(), "a build number names one release"),
            () -> assertFalse(tag("26-alpine").release()),
            () -> assertFalse(tag("25").release())
        );
    }

    @Test
    void isReplacedOnlyByAHigherVersionOfTheSameVariantAndMajorVersion() {
        VariantTag held = tag("26.8.2-alpine");
        assertAll(
            () -> assertTrue(held.replacedBy(tag("26.13.1-alpine"))),
            () -> assertFalse(held.replacedBy(tag("26.13.1-alpine3.24")), "another variant ships another system"),
            () -> assertFalse(held.replacedBy(tag("28.1.0-alpine")), "a new major version is not a repair"),
            () -> assertFalse(held.replacedBy(tag("26.8.1-alpine"))),
            () -> assertFalse(held.replacedBy(tag("26.8.2-alpine"))),
            () -> assertFalse(tag("v2.15.1").replacedBy(tag("2.16.0")), "a v prefix is part of the scheme")
        );
    }

    @Test
    void ordersBuildNumbersAsALevelOfTheVersion() {
        assertAll(
            () -> assertTrue(tag("25.0.4.1_1-jre").replacedBy(tag("25.0.5_8-jre"))),
            () -> assertTrue(tag("25.0.5_8-jre").replacedBy(tag("25.0.5_9-jre")))
        );
    }
}
