package com.example.starter.identity;

import com.example.starter.audit.AuditAction;
import com.example.starter.audit.AuditOutcome;
import com.example.starter.audit.AuditTrail;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every attempt to sign in, as the application records it and as the policy on refused sign-ins counts
 * it. It runs while the reader is being signed in, before anybody holds a role, so it guards nothing:
 * what it changes is decided by the outcome the framework announced, never by the caller.
 *
 * <p>The class is not final because the container wraps it in a proxy, which is a subclass, and the
 * transaction is applied by that proxy.
 */
@Service
public class SignIns {

    private static final int TIMEOUT_SECONDS = 10;

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
     * Records that a sign-in under the given address was let in, which starts its count of refused
     * sign-ins again.
     *
     * @param email the address the reader signed in under
     */
    @Transactional(readOnly = false, timeout = TIMEOUT_SECONDS)
    public void succeeded(String email) {
        this.repository.findByProfileEmail(email)
            .filter(user -> user.getLockout().failedAttempts() > 0)
            .ifPresent(user -> this.repository.save(user.standing(Lockout.clear())));
        this.trail.write(email, AuditAction.SIGN_IN, email, AuditOutcome.SUCCEEDED);
    }

    /**
     * Records that a sign-in under the given address was refused, which counts against the account under
     * it, where there is one, and stops it signing in once the policy says so.
     *
     * @param typed the address the sign-in was attempted under, as it was typed
     */
    @Transactional(readOnly = false, timeout = TIMEOUT_SECONDS)
    public void refused(String typed) {
        String email = UserProfile.canonical(typed);
        this.repository.findByProfileEmail(email).ifPresent(this::counted);
        this.trail.write(email, AuditAction.SIGN_IN, email, AuditOutcome.REFUSED);
    }

    private void counted(User user) {
        Lockout before = user.getLockout();
        Lockout after = before.refused(this.clock.instant());
        this.repository.save(user.standing(after));
        if (!before.equals(after) && after.locks(this.clock.instant())) {
            this.trail.write(
                AuditTrail.APPLICATION, AuditAction.ACCOUNT_LOCKED, user.getProfile().email(), AuditOutcome.SUCCEEDED
            );
        }
    }
}
