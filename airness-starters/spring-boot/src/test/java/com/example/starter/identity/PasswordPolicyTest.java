package com.example.starter.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.web.Wording;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void refusesABlankPassword() {
        assertEquals(Map.of("secret", Wording.of("password.blank")), PasswordPolicy.refusal("   ", "secret"));
    }

    @Test
    void refusesAPasswordShorterThanTheShortestAllowed() {
        assertEquals(Map.of("secret", Wording.of("password.short")), PasswordPolicy.refusal("eleven-char", "secret"));
    }

    @Test
    void refusesAPasswordLongerThanTheEncoderReads() {
        String tooLong = "x".repeat(PasswordPolicy.LONGEST + 1);
        assertEquals(Map.of("secret", Wording.of("password.long")), PasswordPolicy.refusal(tooLong, "secret"));
    }

    @Test
    void admitsAPasswordWithinTheBounds() {
        assertTrue(PasswordPolicy.admits("twelve-chars"));
    }

    @Test
    void doesNotAdmitAShortPassword() {
        assertFalse(PasswordPolicy.admits("short"));
    }
}
