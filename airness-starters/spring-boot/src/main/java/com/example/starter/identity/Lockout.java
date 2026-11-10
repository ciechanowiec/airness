package com.example.starter.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Duration;
import java.time.Instant;

/**
 * The policy on refused sign-ins, as one account stands under it.
 *
 * <p>Five refused sign-ins in a row stop the account signing in for fifteen minutes, whoever typed them.
 * A current password refused on the page that changes it is counted as a refused sign-in, and while the
 * account is stopped that page refuses every current password too.
 * The count starts again once the account is stopped, and a sign-in that is let in clears it. A sign-in
 * refused while the account is stopped neither counts nor extends the stop, so somebody guessing cannot
 * keep the owner out for longer than one stop at a time. The stop lifts on its own once the time has
 * passed. There is no other way to recover from it, so nobody has to be asked.
 *
 * @param failedAttempts how many sign-ins in a row were refused since the count last started
 * @param lockedUntil    the instant until which the account may not sign in, which is in the past for an
 *                       account that may
 */
@Embeddable
public record Lockout(
    @Column(name = "failed_attempts", nullable = false) int failedAttempts,

    @Column(name = "locked_until", nullable = false) Instant lockedUntil
) {

    /**
     * How many sign-ins in a row are refused before the account is stopped.
     */
    static final int LIMIT = 5;

    /**
     * How long the account is stopped once it is.
     */
    static final Duration SPAN = Duration.ofMinutes(15);

    /**
     * Answers the standing of an account nobody has failed to sign in to.
     *
     * @return a lockout with nothing counted and nothing stopped
     */
    public static Lockout clear() {
        return new Lockout(0, Instant.EPOCH);
    }

    /**
     * Answers whether the account may not sign in at the given instant.
     *
     * @param now the instant asked about
     * @return whether the account is stopped then
     */
    public boolean locks(Instant now) {
        return now.isBefore(this.lockedUntil);
    }

    /**
     * Answers the standing of the account after a sign-in to it was refused at the given instant.
     *
     * @param now when the sign-in was refused
     * @return the standing that follows, which stops the account where this refusal reached the limit
     */
    public Lockout refused(Instant now) {
        if (this.locks(now)) {
            return this;
        }
        int attempts = this.failedAttempts + 1;
        return attempts >= LIMIT ? new Lockout(0, now.plus(SPAN)) : new Lockout(attempts, this.lockedUntil);
    }
}
