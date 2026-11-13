package com.example.starter.web;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class PortraitsTest {

    private final Portraits portraits;

    PortraitsTest() {
        this.portraits = new Portraits();
    }

    @Test
    void namesAPortraitInPlainLowerCaseLatinLetters() {
        assertAll(
            () -> assertEquals("piotr-wisniewski", Portraits.slug("Piotr Wiśniewski")),
            () -> assertEquals("lukasz-zolkiewski", Portraits.slug("Łukasz Żółkiewski")),
            () -> assertEquals("anne-marie-o-neil", Portraits.slug("  Anne-Marie O'Neil ")),
            () -> assertEquals("", Portraits.slug("!?"))
        );
    }

    @Test
    void findsThePortraitTheApplicationShipsForAName() {
        assertEquals(Optional.of("/images/people/maja-kowalska.webp"), this.portraits.of("Maja Kowalska"));
    }

    @Test
    void findsNoPortraitForANameTheApplicationShipsNoneFor() {
        assertAll(
            () -> assertEquals(Optional.empty(), this.portraits.of("Somebody Else")),
            () -> assertEquals(Optional.empty(), this.portraits.of("")),
            () -> assertEquals(Optional.empty(), this.portraits.of("Somebody Else"))
        );
    }
}
