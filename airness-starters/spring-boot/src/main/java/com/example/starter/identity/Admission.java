package com.example.starter.identity;

import java.time.Clock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * What turns a password into the hash stored in its place and a form into the account it admits, and
 * what compares a typed password with a stored hash. Every password the application stores passes the
 * policy here first, so no path writes one the policy refuses.
 *
 * <p>A comparison a signed-in reader makes is held to the policy on refused sign-ins as a sign-in is:
 * an account stopped under it proves nothing until the stop lifts, and a comparison that fails counts
 * against the account, because a session left open is otherwise a place to guess its password freely.
 */
@Component
public final class Admission {

    private final PasswordEncoder encoder;

    private final Clock clock;

    private final SignIns signIns;

    /**
     * Hashes every password with the given encoder and stamps every account from the given clock.
     *
     * @param encoder what turns a password into its hash and compares the two
     * @param clock   the clock every instant of the application is read from
     * @param signIns what counts a comparison that fails against the policy on refused sign-ins
     */
    public Admission(PasswordEncoder encoder, Clock clock, SignIns signIns) {
        this.encoder = encoder;
        this.clock = clock;
        this.signIns = signIns;
    }

    /**
     * Answers the account the given form states, admitted now and signing in with the form's password.
     *
     * @param form what was stated about the account
     * @return the account, which the application layer saves
     * @throws IllegalStateException when the password does not meet the policy
     */
    public User admitted(UserForm form) {
        return form.stated(this.hashed(form.password()), this.clock.instant());
    }

    /**
     * Answers the hash a password is stored as.
     *
     * @param password the password chosen
     * @return its hash
     * @throws IllegalStateException when the password does not meet the policy
     */
    public String hashed(String password) {
        if (!PasswordPolicy.admits(password)) {
            throw new IllegalStateException("The password does not meet the password policy");
        }
        return this.encoder.encode(password);
    }

    /**
     * Answers whether the given password is the one the given account signs in with, and the account may
     * sign in now. A password that does not prove the account is counted and recorded as refused, in the
     * transaction of whoever asked.
     *
     * @param password the password typed
     * @param account  the account it is meant to prove
     * @return whether it is that account's password and the account is not stopped
     */
    public boolean proves(String password, User account) {
        boolean proved = !account.getLockout().locks(this.clock.instant())
            && this.encoder.matches(password, account.getPasswordHash());
        if (!proved) {
            this.signIns.disproved(account.getProfile().email());
        }
        return proved;
    }
}
