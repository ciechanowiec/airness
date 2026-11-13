package com.example.starter.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.web.Wording;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private static final String CONTROL = "secret";

    @Test
    void refusesABlankPassword() {
        assertEquals(Map.of(CONTROL, Wording.of("password.blank")), PasswordPolicy.refusal("   ", CONTROL));
    }

    @Test
    void refusesAPasswordShorterThanTheShortestAllowed() {
        assertEquals(Map.of(CONTROL, Wording.of("password.short")), PasswordPolicy.refusal("eleven-char", CONTROL));
    }

    @Test
    void refusesAPasswordLongerThanTheEncoderReads() {
        String tooLong = "x".repeat(PasswordPolicy.LONGEST + 1);
        assertEquals(Map.of(CONTROL, Wording.of("password.long")), PasswordPolicy.refusal(tooLong, CONTROL));
    }

    @Test
    void refusesAPasswordOfFewCharactersThatTakeMoreBytesThanTheEncoderHashes() {
        String accented = "zażółć gęślą jaźń ".repeat(3);
        assertEquals(Map.of(CONTROL, Wording.of("password.long")), PasswordPolicy.refusal(accented, CONTROL));
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
