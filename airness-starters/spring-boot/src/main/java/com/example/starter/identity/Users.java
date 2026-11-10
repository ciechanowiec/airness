package com.example.starter.identity;

import com.example.starter.audit.AuditAction;
import com.example.starter.audit.AuditOutcome;
import com.example.starter.audit.AuditTrail;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * The accounts of the application, and what may be done to one. The transaction boundary, the rule that
 * decides a refusal and the answer to who may ask at all live here, and so does the audit trail of every
 * change: each write records itself in the transaction that makes it.
 *
 * <p>One rule is about the whole list rather than about one account: the application always keeps an
 * administrator who may sign in. Nothing repairs a breach of it afterwards, because the administrator
 * written at startup is written only into an application with no account at all.
 *
 * <p>The class is not final because the container wraps it in a proxy, which is a subclass, and both the
 * transaction and the authorization are applied by that proxy.
 */
@Service
public class Users {

    private static final int TIMEOUT_SECONDS = 10;

    private static final String LAST_ONE = "The application would be left with no administrator who may sign in";

    private final UserRepository repository;

    private final Admission admission;

    private final AuditTrail trail;

    private final Sessions sessions;

    /**
     * Reads and writes the accounts through the given repository.
     *
     * @param repository the accounts as the database holds them
     * @param admission  what hashes a password, turns a form into the account it admits and settles a
     *                   password change
     * @param trail      what records every change to an account
     * @param sessions   the sessions every account is signed in under
     */
    public Users(UserRepository repository, Admission admission, AuditTrail trail, Sessions sessions) {
        this.repository = repository;
        this.admission = admission;
        this.trail = trail;
        this.sessions = sessions;
    }

    /**
     * Admits an account, able to sign in from the moment it is written. There is no self-registration, so
     * this is how every account but the first administrator comes to exist.
     *
     * @param form what an administrator stated about the account, including its password
     * @throws IllegalStateException when an account already signs in under that address, or when the
     *                               password does not meet the policy
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = false, timeout = TIMEOUT_SECONDS)
    public void register(UserForm form) {
        String email = form.profile().email();
        if (this.repository.existsByProfileEmail(email)) {
            throw new IllegalStateException("An account already signs in under %s".formatted(email));
        }
        this.admitted(form);
        this.trail.write(actor(), AuditAction.ACCOUNT_CREATED, email, AuditOutcome.SUCCEEDED);
    }

    /**
     * Restates the account under the given reference as the form now describes it, keeping its password
     * and whether it may sign in. Where its address or its role changes, every session it is signed in
     * under ends, so it signs in again under what it now is.
     *
     * @param reference the reference the account is known by outside the database
     * @param form      what an administrator stated about the account
     * @throws IllegalStateException when nobody carries that reference, when another account already signs
     *                               in under the address given, or when the change would leave the
     *                               application with no administrator who may sign in
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = false, timeout = TIMEOUT_SECONDS)
    public void restate(UUID reference, UserForm form) {
        User user = this.found(reference);
        this.refuseRestating(user, form);
        // Both facts are read before the save, because saving merges the restated account into the one
        // read above, which then says what the restatement says.
        String before = user.getProfile().email();
        User restated = user.describedBy(form.profile(), form.chosen());
        boolean differently = signsInDifferently(user, restated);
        this.repository.save(restated);
        if (differently) {
            this.sessions.end(before);
        }
        this.trail.write(actor(), AuditAction.ACCOUNT_RESTATED, form.profile().email(), AuditOutcome.SUCCEEDED);
    }

    /**
     * Lets the account under the given reference sign in, or stops it. Stopping it ends every session it
     * is signed in under, so it is refused from its next request on.
     *
     * @param reference the reference the account is known by outside the database
     * @param allowed   whether the account may sign in at all
     * @throws IllegalStateException when nobody carries that reference, or when stopping it would leave the
     *                               application with no administrator who may sign in
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = false, timeout = TIMEOUT_SECONDS)
    public void enabling(UUID reference, boolean allowed) {
        User user = this.found(reference);
        if (!allowed && this.sole(user)) {
            throw new IllegalStateException(LAST_ONE);
        }
        this.repository.save(user.enabled(allowed));
        String email = user.getProfile().email();
        if (!allowed) {
            this.sessions.end(email);
        }
        AuditAction action = allowed ? AuditAction.ACCOUNT_ENABLED : AuditAction.ACCOUNT_DISABLED;
        this.trail.write(actor(), action, email, AuditOutcome.SUCCEEDED);
    }

    /**
     * Answers whether the given password is the one the signed-in reader signs in with, which the page
     * that changes it asks before it changes anything. A password that is not theirs counts against their
     * account as a refused sign-in does and is recorded as a refused change, and while the account is
     * stopped under the policy on refused sign-ins no password is theirs.
     *
     * <p>It holds no transaction of its own: the comparison takes a quarter of a second, and the outcome is
     * settled in a transaction of its own once the comparison is over.
     *
     * @param password the password the reader typed as their current one
     * @return whether it is theirs, and their account is not stopped
     */
    @PreAuthorize("isAuthenticated()")
    public boolean proves(String password) {
        return this.admission.proves(password, this.reader());
    }

    /**
     * Changes the password the signed-in reader signs in with, and ends every other session of the account.
     *
     * <p>The current password is proved, and the chosen one hashed, before any transaction begins, so no
     * connection is held through either comparison and a refused proof stays counted although the change
     * is refused. The new password is then written on the account as the database holds it then, held for
     * the write, rather than on the reading the proof began with, and only while that is still the account
     * proved: the same one, with the same password, enabled and not stopped. One that changed meanwhile,
     * by another session changing the password first or an administrator stopping it, keeps what it has,
     * and the change is recorded as refused.
     *
     * @param current the password the reader signs in with today
     * @param chosen  the password the reader signs in with from now on
     * @throws IllegalStateException when the current password is not theirs, when their account is stopped
     *                               under the policy on refused sign-ins, when the chosen one does not
     *                               meet the policy, or when the account changed while the change was made
     */
    @PreAuthorize("isAuthenticated()")
    public void changePassword(String current, String chosen) {
        User reader = this.reader();
        if (!this.admission.proves(current, reader)) {
            throw new IllegalStateException("The current password is not the reader's");
        }
        // A session opened by whoever knew the old password must not outlive it. The one making the
        // change stays, because its reader has just proved the password.
        String kept = Optional.ofNullable(RequestContextHolder.getRequestAttributes())
            .map(RequestAttributes::getSessionId)
            .orElse("");
        if (!this.admission.changes(reader, chosen, kept)) {
            throw new IllegalStateException("The account changed while its password was being changed");
        }
    }

    /**
     * Writes the first administrator, so that an application starting against an empty database can be
     * signed in to at all. It asks for no role, because it runs before any account exists. What stands in
     * the place of a role is the rule that it writes nothing once the application has an account.
     *
     * @param profile  how the first administrator is named and reached
     * @param password the password that administrator signs in with
     * @return the reference of the administrator written, or nothing when an account already exists
     * @throws IllegalStateException when the password does not meet the policy, which a blank one does not
     */
    @PreAuthorize("permitAll()")
    @Transactional(readOnly = false, timeout = TIMEOUT_SECONDS)
    public Optional<UUID> bootstrap(UserProfile profile, String password) {
        if (this.repository.count() > 0) {
            return Optional.empty();
        }
        if (!PasswordPolicy.admits(password)) {
            throw new IllegalStateException(
                "The first administrator was not written, because the password configured for it is blank or "
                    + "does not meet the password policy"
            );
        }
        UUID reference = this.admitted(new UserForm(profile, Role.ADMIN, password));
        this.trail.write(
            AuditTrail.APPLICATION, AuditAction.ADMINISTRATOR_BOOTSTRAPPED, profile.email(), AuditOutcome.SUCCEEDED
        );
        return Optional.of(reference);
    }

    // The two rules a restatement is held to, which the page asks before it posts and which hold here
    // whatever asked.
    private void refuseRestating(User user, UserForm form) {
        String email = form.profile().email();
        if (this.repository.existsByProfileEmailAndReferenceNot(email, user.getReference())) {
            throw new IllegalStateException("Another account already signs in under %s".formatted(email));
        }
        if (form.chosen() != Role.ADMIN && this.sole(user)) {
            throw new IllegalStateException(LAST_ONE);
        }
    }

    // Whether the account now signs in under another address or with another role, which is what its
    // sessions were opened under.
    private static boolean signsInDifferently(User before, User after) {
        return !before.getProfile().email().equals(after.getProfile().email())
            || before.getAccess().role() != after.getAccess().role();
    }

    private UUID admitted(UserForm form) {
        return this.repository.save(this.admission.admitted(form)).getReference();
    }

    // Whether this account is the only administrator left who may sign in. An account disabled already,
    // or holding another role, is nobody's last administrator whatever the count says.
    private boolean sole(User user) {
        UserAccess access = user.getAccess();
        return access.enabled() && access.role() == Role.ADMIN && this.repository.enabledUnder(Role.ADMIN) == 1;
    }

    private User found(UUID reference) {
        return this.repository.findByReference(reference)
            .orElseThrow(() -> new IllegalStateException("Nobody carries the reference %s".formatted(reference)));
    }

    private User reader() {
        return Reader.email()
            .flatMap(this.repository::findByProfileEmail)
            .orElseThrow(Users::readerless);
    }

    private static IllegalStateException readerless() {
        return new IllegalStateException("The reader of this request has no account");
    }

    private static String actor() {
        return Reader.email().orElse(AuditTrail.APPLICATION);
    }
}
