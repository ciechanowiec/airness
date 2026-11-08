package com.example.starter.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UserProfileTest {

    @Test
    void holdsAnAddressInItsCanonicalSpelling() {
        assertEquals("ada@example.test", new UserProfile("  Ada@Example.TEST ", "Ada").email());
    }

    @Test
    void standsAnEmptyNameInThePlaceOfNone() {
        assertEquals("", new UserProfile(null, null).fullName());
    }

    @Test
    void trimsTheNameItIsShownUnder() {
        assertEquals("Ada Lovelace", new UserProfile("ada@starter.test", "  Ada Lovelace ").fullName());
    }
}
