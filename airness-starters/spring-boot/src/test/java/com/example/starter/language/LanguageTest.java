package com.example.starter.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LanguageTest {

    @Test
    void answersPolishToABrowserAskingForItInAnyCountry() {
        assertEquals(Optional.of(Language.POLISH), Language.of(Locale.of("pl", "PL")));
    }

    @Test
    void answersNothingToALanguageTheApplicationDoesNotSpeak() {
        assertTrue(Language.of(Locale.GERMAN).isEmpty());
    }

    @Test
    void readsALanguageBackFromItsName() {
        assertEquals(Optional.of(Language.ENGLISH), Language.named("ENGLISH"));
    }

    @Test
    void readsNoLanguageFromANameNoneCarries() {
        assertTrue(Language.named("KLINGON").isEmpty());
    }

    @Test
    void namesEachLanguageInItsOwnWords() {
        assertEquals("Polski", Language.POLISH.endonym());
    }
}
