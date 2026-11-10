package com.example.starter.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.web.Wording;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The policy a chosen password is held to. The limit is a count of the bytes BCrypt reads, so it is asked
 * with letters UTF-8 writes in two, three and four bytes as well as in one, each just within the limit and
 * just over it.
 */
class PasswordPolicyTest {

    private static final String CONTROL = "secret";

    // Thirty-six letters of two bytes, twenty-four of three and eighteen of four each fill the limit.
    private static final String TWO_BYTE_FULL = "ą".repeat(36);

    private static final String THREE_BYTE_FULL = "€".repeat(24);

    private static final String FOUR_BYTE_FULL = "😀".repeat(18);

    // One byte more than any of the above.
    private static final String ONE_MORE = "a";

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
    void admitsAPasswordWithinTheBounds() {
        assertTrue(PasswordPolicy.admits("twelve-chars"));
    }

    @Test
    void doesNotAdmitAShortPassword() {
        assertFalse(PasswordPolicy.admits("short"));
    }

    @Test
    void admitsTwoByteLettersThatFillTheLimit() {
        assertTrue(PasswordPolicy.admits(TWO_BYTE_FULL));
    }

    @Test
    void refusesTwoByteLettersOneByteOverTheLimit() {
        assertEquals(
            Map.of(CONTROL, Wording.of("password.long")), PasswordPolicy.refusal(TWO_BYTE_FULL + ONE_MORE, CONTROL)
        );
    }

    @Test
    void admitsThreeByteLettersThatFillTheLimit() {
        assertTrue(PasswordPolicy.admits(THREE_BYTE_FULL));
    }

    @Test
    void refusesThreeByteLettersOneByteOverTheLimit() {
        assertFalse(PasswordPolicy.admits(THREE_BYTE_FULL + ONE_MORE));
    }

    @Test
    void admitsFourByteLettersThatFillTheLimit() {
        assertTrue(PasswordPolicy.admits(FOUR_BYTE_FULL));
    }

    @Test
    void refusesFourByteLettersOneByteOverTheLimit() {
        assertFalse(PasswordPolicy.admits(FOUR_BYTE_FULL + ONE_MORE));
    }
}
