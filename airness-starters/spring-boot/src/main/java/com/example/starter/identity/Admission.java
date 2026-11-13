package com.example.starter.identity;

import java.time.Clock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * What turns a password into the hash stored in its place and a form into the account it admits, and
 * what compares a typed password with a stored hash. Every password the application stores passes the
 * policy here first, so no path writes one the policy refuses, and every comparison made here counts
 * under the policy on refused sign-ins, so no page lets somebody guess a password more often than the
 * sign-in page does.
 */
@Component
public final class Admission {

    private final PasswordEncoder encoder;

    private final SignIns signIns;

    private final Clock clock;

    /**
     * Hashes every password with the given encoder, counts every comparison through the given sign-ins and
     * stamps every account from the given clock.
     *
     * @param encoder what turns a password into its hash and compares the two
     * @param signIns every attempt to prove a password, as the policy counts it
     * @param clock   the clock every instant of the application is read from
     */
    public Admission(PasswordEncoder encoder, SignIns signIns, Clock clock) {
        this.encoder = encoder;
        this.signIns = signIns;
        this.clock = clock;
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
     * Answers whether the given password is the one the given account signs in with, counting the attempt
     * under the policy on refused sign-ins. A locked account is answered as one without comparing.
     *
     * @param password the password typed
     * @param account  the account it is meant to prove
     * @return how the attempt turned out
     */
    public Proof proves(String password, User account) {
        if (!this.signIns.reserved(account.getProfile().email())) {
            return Proof.LOCKED;
        }
        return this.compared(password, account);
    }

    // Compares the password once an attempt was taken for it, and gives the attempt back or keeps it.
    private Proof compared(String password, User account) {
        String email = account.getProfile().email();
        boolean matches = this.encoder.matches(password, account.getPasswordHash());
        if (matches) {
            this.signIns.proved(email);
        } else {
            this.signIns.disproved(email);
        }
        return matches ? Proof.PROVEN : Proof.WRONG;
    }
}
