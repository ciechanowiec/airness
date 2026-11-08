package com.example.starter.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LockoutTest {

    private static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");

    @Test
    void stopsNothingBeforeAnySignInIsRefused() {
        assertFalse(Lockout.clear().locks(NOW));
    }

    @Test
    void countsARefusedSignIn() {
        assertEquals(1, Lockout.clear().refused(NOW).failedAttempts());
    }

    @Test
    void stopsTheAccountOnceTheLimitIsReached() {
        Lockout standing = Stream.iterate(Lockout.clear(), refused -> refused.refused(NOW))
            .skip(Lockout.LIMIT)
            .findFirst()
            .orElseThrow();
        assertTrue(standing.locks(NOW));
    }

    @Test
    void liftsTheStopOnceItsSpanHasPassed() {
        Lockout stopped = new Lockout(0, NOW.plus(Lockout.SPAN));
        assertFalse(stopped.locks(NOW.plus(Lockout.SPAN)));
    }

    @Test
    void neitherCountsNorExtendsARefusalWhileStopped() {
        Lockout stopped = new Lockout(0, NOW.plus(Lockout.SPAN));
        assertEquals(stopped, stopped.refused(NOW));
    }
}
