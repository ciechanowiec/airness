package com.example.starter.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.web.Wording;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PasswordFormTest {

    @Test
    void refusesAChosenPasswordThePolicyRefuses() {
        assertEquals(Map.of("chosen", Wording.of("password.short")), new PasswordForm("current", "short").unchosen());
    }

    @Test
    void acceptsAChosenPasswordThePolicyAdmits() {
        assertTrue(new PasswordForm("current", "a-long-enough-one").unchosen().isEmpty());
    }

    @Test
    void standsEmptyPasswordsInThePlaceOfNothing() {
        assertEquals(new PasswordForm("", ""), new PasswordForm(null, null));
    }
}
