package com.example.starter.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;

/**
 * The policy on refused sign-ins, as one account stands under it.
 *
 * <p>Five refused attempts in a row lock the account for fifteen minutes, whoever made them. An attempt
 * is a sign-in, or a current password typed on the page that changes it. Each attempt is counted when it
 * is made, before the password is compared, so attempts made at once count together and no more of them
 * are compared than the policy allows. The count starts again once the account is locked, and an attempt
 * that proves the password clears it. An attempt made while the account is locked neither counts nor
 * extends the lock, so somebody guessing cannot keep the owner out for longer than one lock at a time.
 * The lock lifts on its own once the time has passed. There is no other way to recover from it, so
 * nobody has to be asked.
 *
 * <p>The statements of {@link UserRepository} that raise, lock and clear the count each change it in one
 * step at the database, because a count read, raised and saved back would let attempts made at once
 * overwrite each other.
 *
 * @param failedAttempts how many attempts in a row were counted since the count last started
 * @param lockedUntil    the instant until which the account may not sign in, which is in the past for an
 *                       account that may
 */
@Embeddable
public record Lockout(
    @Column(name = "failed_attempts", nullable = false) int failedAttempts,

    @Column(name = "locked_until", nullable = false) Instant lockedUntil
) {

    /**
     * How many attempts in a row are refused before the account is locked.
     */
    static final int LIMIT = 5;

    /**
     * Answers the lockout of an account nobody has failed to sign in to.
     *
     * @return a lockout with nothing counted and nothing locked
     */
    public static Lockout clear() {
        return new Lockout(0, Instant.EPOCH);
    }
}
