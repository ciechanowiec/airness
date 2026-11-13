package com.example.starter.identity;

import com.example.starter.TransactionTimeout;
import com.example.starter.audit.AuditAction;
import com.example.starter.audit.AuditOutcome;
import com.example.starter.audit.AuditTrail;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every attempt to prove a password, as the policy on refused sign-ins counts it, and every sign-in, as
 * the application records it. It runs while the reader is being signed in, before anybody holds a role,
 * so it guards nothing: what it changes is decided by the outcome the framework announced, never by the
 * caller.
 *
 * <p>An attempt is counted before its password is compared and cleared once the password is proved, so
 * attempts made at once cannot all be compared before the first of them is counted.
 *
 * <p>The class is not final because the container wraps it in a proxy, which is a subclass, and the
 * transaction is applied by that proxy.
 */
@Service
public class SignIns {

    // How long an account is locked once every attempt the policy allows was refused.
    private static final Duration LOCK = Duration.ofMinutes(15);

    private final UserRepository repository;

    private final AuditTrail trail;

    private final Clock clock;

    /**
     * Records sign-ins through the given trail and counts them on the accounts of the given repository.
     *
     * @param repository the accounts as the database holds them
     * @param trail      what records every outcome
     * @param clock      the clock every instant of the application is read from
     */
    public SignIns(UserRepository repository, AuditTrail trail, Clock clock) {
        this.repository = repository;
        this.trail = trail;
        this.clock = clock;
    }

    /**
     * Takes one of the attempts the policy leaves the account under the given address before it is
     * locked, which is asked before its password is compared.
     *
     * @param email the address in its canonical spelling
     * @return whether an attempt was left, which it is not while the account is locked
     */
    @Transactional(readOnly = false, timeout = TransactionTimeout.SECONDS)
    public boolean reserved(String email) {
        return this.repository.reserved(email, Lockout.LIMIT, this.clock.instant()) > 0;
    }

    /**
     * Records that a sign-in under the given address was let in, which starts its count again.
     *
     * @param email the address the reader signed in under
     */
    @Transactional(readOnly = false, timeout = TransactionTimeout.SECONDS)
    public void succeeded(String email) {
        this.repository.cleared(email);
        this.trail.write(email, AuditAction.SIGN_IN, email, AuditOutcome.SUCCEEDED);
    }

    /**
     * Records that a sign-in under the given address was refused, which locks the account under it once
     * it has taken every attempt the policy allows.
     *
     * @param typed the address the sign-in was attempted under, as it was typed
     */
    @Transactional(readOnly = false, timeout = TransactionTimeout.SECONDS)
    public void refused(String typed) {
        String email = UserProfile.canonical(typed);
        this.lockedIfSpent(email);
        this.trail.write(email, AuditAction.SIGN_IN, email, AuditOutcome.REFUSED);
    }

    /**
     * Starts the count of the account under the given address again, because a password was proved.
     *
     * @param email the address in its canonical spelling
     */
    @Transactional(readOnly = false, timeout = TransactionTimeout.SECONDS)
    public void proved(String email) {
        this.repository.cleared(email);
    }

    /**
     * Locks the account under the given address where the attempt just refused was the last the policy
     * allows, and records the lock.
     *
     * @param email the address in its canonical spelling
     */
    @Transactional(readOnly = false, timeout = TransactionTimeout.SECONDS)
    public void disproved(String email) {
        this.lockedIfSpent(email);
    }

    private void lockedIfSpent(String email) {
        Instant until = this.clock.instant().plus(LOCK);
        if (this.repository.locked(email, Lockout.LIMIT, until) > 0) {
            this.repository.findByProfileEmail(email).ifPresent(
                user -> this.trail.write(
                    AuditTrail.APPLICATION, AuditAction.ACCOUNT_LOCKED, user.getReference().toString(),
                    AuditOutcome.SUCCEEDED
                )
            );
        }
    }
}
