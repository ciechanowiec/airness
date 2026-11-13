package com.example.starter.identity;

import com.example.starter.TransactionTimeout;
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
 * change: each write records itself in the transaction that makes it. The trail names the account changed
 * and the reader who changed it by the reference of each, which no restatement changes, so an entry
 * still names the same account after its address has changed.
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

    private static final String LAST_ONE = "The application would be left with no administrator who may sign in";

    private final UserRepository repository;

    private final Admission admission;

    private final AuditTrail trail;

    private final Sessions sessions;

    /**
     * Reads and writes the accounts through the given repository.
     *
     * @param repository the accounts as the database holds them
     * @param admission  what hashes a password and turns a form into the account it admits
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
    @Transactional(readOnly = false, timeout = TransactionTimeout.SECONDS)
    public void register(UserForm form) {
        String email = form.profile().email();
        if (this.repository.existsByProfileEmail(email)) {
            throw new IllegalStateException("An account already signs in under %s".formatted(email));
        }
        UUID admitted = this.admitted(form);
        this.trail.write(this.actor(), AuditAction.ACCOUNT_CREATED, admitted.toString(), AuditOutcome.SUCCEEDED);
    }

    /**
     * Restates the account under the given reference as the form now describes it, keeping its password
     * and whether it may sign in. Where its address or its role changes, every session it is signed in
     * under ends, so it signs in again under what it now is. A changed role is recorded as an entry of its
     * own, because it changes what the account may do rather than how it is named.
     *
     * @param reference the reference the account is known by outside the database
     * @param form      what an administrator stated about the account
     * @throws IllegalStateException when nobody carries that reference, when another account already signs
     *                               in under the address given, or when the change would leave the
     *                               application with no administrator who may sign in
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = false, timeout = TransactionTimeout.SECONDS)
    public void restate(UUID reference, UserForm form) {
        User user = this.found(reference);
        this.refuseRestating(user, form);
        // Both facts are read before the save, because saving merges the restated account into the one
        // read above, which then says what the restatement says.
        String before = user.getProfile().email();
        User restated = user.describedBy(form.profile(), form.chosen());
        boolean differently = signsInDifferently(user, restated);
        boolean reassigned = user.getAccess().role() != restated.getAccess().role();
        this.repository.save(restated);
        this.recordRestatement(reference, reassigned);
        if (differently) {
            this.sessions.end(before);
        }
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
    @Transactional(readOnly = false, timeout = TransactionTimeout.SECONDS)
    public void enabling(UUID reference, boolean allowed) {
        User user = this.found(reference);
        if (!allowed && this.sole(user)) {
            throw new IllegalStateException(LAST_ONE);
        }
        this.repository.save(user.enabled(allowed));
        if (!allowed) {
            this.sessions.end(user.getProfile().email());
        }
        AuditAction action = allowed ? AuditAction.ACCOUNT_ENABLED : AuditAction.ACCOUNT_DISABLED;
        this.trail.write(this.actor(), action, reference.toString(), AuditOutcome.SUCCEEDED);
    }

    /**
     * Answers whether the given password is the one the signed-in reader signs in with, which the page
     * that changes it asks before it changes anything. The attempt counts under the policy on refused
     * sign-ins like a sign-in does, and one that is refused is recorded, so whoever holds somebody else's
     * session can neither guess their password more often than the sign-in page allows nor guess unseen.
     *
     * @param password the password the reader typed as their current one
     * @return how the attempt turned out
     */
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = false, timeout = TransactionTimeout.SECONDS)
    public Proof proves(String password) {
        User user = this.reader();
        Proof proof = this.admission.proves(password, user);
        if (proof != Proof.PROVEN) {
            String target = user.getReference().toString();
            this.trail.write(target, AuditAction.PASSWORD_CHANGED, target, AuditOutcome.REFUSED);
        }
        return proof;
    }

    /**
     * Changes the password the signed-in reader signs in with, and ends every other session of the account.
     *
     * @param current the password the reader signs in with today
     * @param chosen  the password the reader signs in with from now on
     * @throws IllegalStateException when the current password is not theirs, or the chosen one does not
     *                               meet the policy
     */
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = false, timeout = TransactionTimeout.SECONDS)
    public void changePassword(String current, String chosen) {
        User user = this.reader();
        if (this.admission.proves(current, user) != Proof.PROVEN) {
            throw new IllegalStateException("The current password is not the reader's");
        }
        this.repository.save(user.withPassword(this.admission.hashed(chosen)));
        // A session opened by whoever knew the old password must not outlive it. The one making the
        // change stays, because its reader has just proved the password.
        this.sessions.endOthers(
            user.getProfile().email(),
            Optional.ofNullable(RequestContextHolder.getRequestAttributes()).map(RequestAttributes::getSessionId)
                .orElse("")
        );
        String target = user.getReference().toString();
        this.trail.write(target, AuditAction.PASSWORD_CHANGED, target, AuditOutcome.SUCCEEDED);
    }

    /**
     * Writes the first administrator, so that an application starting against an empty database can be
     * signed in to at all. It asks for no role, because it runs before any account exists. What stands in
     * the place of a role is the rule that it writes nothing once the application has an account.
     *
     * @param profile  how the first administrator is named and reached
     * @param password the password that administrator signs in with
     * @return the reference of the administrator written, or nothing when an account already exists
     * @throws IllegalStateException when the password does not meet the policy
     */
    @PreAuthorize("permitAll()")
    @Transactional(readOnly = false, timeout = TransactionTimeout.SECONDS)
    public Optional<UUID> bootstrap(UserProfile profile, String password) {
        if (this.repository.count() > 0) {
            return Optional.empty();
        }
        UUID reference = this.admitted(new UserForm(profile, Role.ADMIN, password));
        this.trail.write(
            AuditTrail.APPLICATION, AuditAction.ADMINISTRATOR_BOOTSTRAPPED, reference.toString(), AuditOutcome.SUCCEEDED
        );
        return Optional.of(reference);
    }

    // A restatement, and beside it the change of role it made, if it made one.
    private void recordRestatement(UUID reference, boolean reassigned) {
        String actor = this.actor();
        String target = reference.toString();
        this.trail.write(actor, AuditAction.ACCOUNT_RESTATED, target, AuditOutcome.SUCCEEDED);
        if (reassigned) {
            this.trail.write(actor, AuditAction.ROLE_CHANGED, target, AuditOutcome.SUCCEEDED);
        }
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

    private boolean sole(User user) {
        return user.getAccess().soleAdministrator(this.repository.enabledUnder(Role.ADMIN));
    }

    private User found(UUID reference) {
        return this.repository.findByReference(reference)
            .orElseThrow(() -> new IllegalStateException("Nobody carries the reference %s".formatted(reference)));
    }

    private User reader() {
        return Reader.email()
            .flatMap(this.repository::findByProfileEmail)
            .orElseThrow(() -> new IllegalStateException("The reader of this request has no account"));
    }

    // Who the trail names as having made a change: the signed-in reader, by the reference of their account.
    private String actor() {
        return this.reader().getReference().toString();
    }
}
