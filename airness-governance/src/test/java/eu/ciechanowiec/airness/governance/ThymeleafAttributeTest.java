package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ThymeleafAttributeTest {

    @Test
    void recognizesTheDialectUnderThePrefix() {
        assertTrue(ThymeleafAttribute.isDialect("th:text"));
        assertTrue(ThymeleafAttribute.isDialect("th:fragment"));
    }

    @Test
    void recognizesTheDialectUnderTheDataSpelling() {
        assertTrue(ThymeleafAttribute.isDialect("data-th-text"));
        assertTrue(ThymeleafAttribute.isDialect("data-th-fragment"));
    }

    @Test
    void passesOverPlainAndLookalikeAttributes() {
        assertFalse(ThymeleafAttribute.isDialect("text"));
        assertFalse(ThymeleafAttribute.isDialect("data-text"));
        assertFalse(ThymeleafAttribute.isDialect("data-th"));
        assertFalse(ThymeleafAttribute.isDialect("xth:text"));
    }

    @Test
    void comparesInTheCaseTheCallerChose() {
        assertFalse(ThymeleafAttribute.isDialect("TH:text"));
        assertFalse(ThymeleafAttribute.isDialect("DATA-TH-text"));
    }

    @Test
    void spellsOneAttributeUnderThePrefixAndTheDataSpelling() {
        assertEquals(Set.of("th:replace", "data-th-replace"), ThymeleafAttribute.spellings("replace"));
    }

    @Test
    void spellsSeveralAttributesTogether() {
        assertEquals(
            Set.of("th:ref", "data-th-ref", "th:inline", "data-th-inline"),
            ThymeleafAttribute.spellings("ref", "inline")
        );
    }

    @Test
    void spellsNothingForNoAttribute() {
        assertTrue(ThymeleafAttribute.spellings().isEmpty());
    }
}
