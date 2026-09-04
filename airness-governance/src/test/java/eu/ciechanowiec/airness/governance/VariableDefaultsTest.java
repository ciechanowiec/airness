package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * A variable written with a default resolves to the default, and one written without stays put.
 */
class VariableDefaultsTest {

    @Test
    void resolvesBothDefaultSpellings() {
        assertEquals("mongo:7.0.14", VariableDefaults.applied("mongo:${TAG:-7.0.14}"), "the colon-dash form");
        assertEquals("mongo:7.0.14", VariableDefaults.applied("mongo:${TAG-7.0.14}"), "and the dash form");
    }

    @Test
    void leavesAVariableWithoutADefault() {
        assertEquals("mongo:${TAG}", VariableDefaults.applied("mongo:${TAG}"), "nothing says what it holds");
        assertEquals("mongo:${TAG:?set}", VariableDefaults.applied("mongo:${TAG:?set}"), "nor does an error form");
    }
}
