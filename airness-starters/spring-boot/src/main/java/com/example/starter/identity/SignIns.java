package com.example.starter.identity;

import com.example.starter.audit.AuditAction;
import com.example.starter.audit.AuditOutcome;
import com.example.starter.audit.AuditTrail;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every attempt to prove a password, as the application records it and as the policy on refused sign-ins
 * counts it: a sign-in, and the current password a signed-in reader types to change it, together with the
 * change such a proof lets through. It runs while the reader is being signed in, before anybody holds a
 * role, so it guards nothing: what it changes is decided by the outcome the framework or the application
 * layer announced, never by the caller.
 *
 * <p>Each change to the count is made on the account's row held for the transaction, read as it stands
 * then, so attempts arriving at once are counted one after another and none of them writes back a
 * standing it read before another attempt, or an administrator, changed it.
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

    private final Sessions sessions;

    /**
     * Records sign-ins through the given trail and counts them on the accounts of the given repository.
     *
     * @param repository the accounts as the database holds them
     * @param trail      what records every outcome
     * @param clock      the clock every instant of the application is read from
     * @param sessions   the sessions a changed password ends
     */
    public SignIns(UserRepository repository, AuditTrail trail, Clock clock, Sessions sessions) {
        this.repository = repository;
        this.trail = trail;
        this.clock = clock;
        this.sessions = sessions;
    }

    /**
     * Records that a sign-in under the given address was let in, which starts its count of refused
     * sign-ins again.
     *
     * @param email the address the reader signed in under
     */
    @Transactional(readOnly = false, timeout = TIMEOUT_SECONDS)
    public void succeeded(String email) {
        this.repository.heldUnder(email)
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
        this.countedAgainst(email);
        this.trail.write(email, AuditAction.SIGN_IN, email, AuditOutcome.REFUSED);
    }

    /**
     * Settles whether the current password a signed-in reader typed, on the page that changes it, proves
     * their account. One that did not match, or that matched while the account is stopped as it stands now,
     * counts against the account exactly as a refused sign-in does, because both are a guess at the same
     * password, and the change it was meant to prove is recorded as refused.
     *
     * <p>It runs in a transaction of its own. The caller's transaction read the account before the
     * comparison and keeps that reading, so a count made in it would start from a standing other attempts
     * have since changed, and a caller that refuses the change undoes its whole transaction, the count with
     * it.
     *
     * @param email   the address the reader signs in with
     * @param matched whether the password typed matched the account as it was read before the comparison
     * @return whether the password proves the account
     */
    @Transactional(readOnly = false, timeout = TIMEOUT_SECONDS, propagation = Propagation.REQUIRES_NEW)
    public boolean settled(String email, boolean matched) {
        Optional<User> held = this.repository.heldUnder(email);
        boolean proved = matched && held.filter(user -> !user.getLockout().locks(this.clock.instant())).isPresent();
        if (!proved) {
            held.ifPresent(this::counted);
            this.trail.write(email, AuditAction.PASSWORD_CHANGED, email, AuditOutcome.REFUSED);
        }
        return proved;
    }

    /**
     * Writes the password a signed-in reader chose, once their current password proved the account, and ends
     * every other session of it. The account is written as the database holds it now, held for the write,
     * and only while it is still the account proved: the same one, with the same password, enabled and not
     * stopped. One that changed meanwhile, because another session changed the password first or an
     * administrator stopped it, keeps what it has, and the change is recorded as refused.
     *
     * @param proved the account as it was read when its current password proved it
     * @param hash   the hash of the password chosen
     * @param kept   the identifier of the one session of the account that stays, or empty for none
     * @return whether the password was changed
     */
    @Transactional(readOnly = false, timeout = TIMEOUT_SECONDS)
    public boolean replaced(User proved, String hash, String kept) {
        String email = proved.getProfile().email();
        Optional<User> held = this.repository.heldUnder(email)
            .filter(account -> this.standsAsProved(account, proved));
        if (held.isEmpty()) {
            this.trail.write(email, AuditAction.PASSWORD_CHANGED, email, AuditOutcome.REFUSED);
            return false;
        }
        this.repository.save(held.orElseThrow().withPassword(hash));
        this.sessions.endOthers(email, kept);
        this.trail.write(email, AuditAction.PASSWORD_CHANGED, email, AuditOutcome.SUCCEEDED);
        return true;
    }

    // Whether the account held for the write is still the one the current password proved.
    private boolean standsAsProved(User held, User proved) {
        return held.getReference().equals(proved.getReference())
            && held.getPasswordHash().equals(proved.getPasswordHash())
            && held.getAccess().enabled()
            && !held.getLockout().locks(this.clock.instant());
    }

    private void countedAgainst(String email) {
        this.repository.heldUnder(email).ifPresent(this::counted);
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
