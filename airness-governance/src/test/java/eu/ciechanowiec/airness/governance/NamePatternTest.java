package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A pattern is either the name itself or a prefix ending in an asterisk, and nothing in between.
 */
class NamePatternTest {

    @Test
    void matchesTheNameItselfAndNothingLonger() {
        assertTrue(NamePattern.matches("mongo", "mongo"), "the name is its own pattern");
        assertFalse(NamePattern.matches("mongo", "mongodb"), "without an asterisk a longer name is another name");
    }

    @Test
    void matchesEveryNameUnderAPrefix() {
        assertTrue(NamePattern.matches("bitnami/*", "bitnami/redis"), "an asterisk covers the namespace");
        assertTrue(NamePattern.matches("gvenzl/oracle-*", "gvenzl/oracle-free"), "and a partial segment");
        assertFalse(NamePattern.matches("bitnami/*", "bitnamilegacy/redis"), "but not a name beside the prefix");
    }
}
