package com.example.starter.identity;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * What the application knows about somebody trying to sign in, answered to the part of the framework
 * that checks a password.
 *
 * <p>This is the one reader of accounts that holds the repository rather than the application layer.
 * It has to: it runs while the caller holds no role, because deciding who they are is what it is for.
 * Every attempt on an account takes one of the attempts the policy on refused sign-ins allows before
 * the password is compared, and an account that has none left, or is disabled, is answered as one.
 * The framework compares the password of every attempt all the same, including one under an address
 * nobody signs in with, so each refusal takes as long as any other. The sign-in page says the same thing
 * about every refusal, so none of them tells whoever is guessing which addresses exist.
 *
 * <p>The {@link User} imported below is the framework's own, and not the persistent type of the same
 * name, which is never named in this file.
 */
@Component
public final class Credentials implements UserDetailsService {

    private static final String UNKNOWN = "No account signs in under that address";

    private final UserRepository repository;

    private final SignIns signIns;

    /**
     * Reads the accounts through the given repository and counts every attempt through the given sign-ins.
     *
     * @param repository the accounts as the database holds them
     * @param signIns    every attempt to prove a password, as the policy counts it
     */
    public Credentials(UserRepository repository, SignIns signIns) {
        this.repository = repository;
        this.signIns = signIns;
    }

    /**
     * Answers what the framework needs to decide whether somebody may sign in, having counted the attempt.
     *
     * @param typed the address somebody is trying to sign in under, as they typed it
     * @return the account under that address, as the framework reads one
     * @throws UsernameNotFoundException when nobody signs in under that address
     */
    @Override
    public UserDetails loadUserByUsername(String typed) {
        String email = UserProfile.canonical(typed);
        return this.repository.findByProfileEmail(email)
            .map(
                account -> User.withUsername(account.getProfile().email())
                    .password(account.getPasswordHash())
                    .roles(account.getAccess().role().name())
                    .disabled(!account.getAccess().enabled())
                    .accountLocked(!this.signIns.reserved(email))
                    .build()
            ).orElseThrow(() -> new UsernameNotFoundException(UNKNOWN));
    }
}
