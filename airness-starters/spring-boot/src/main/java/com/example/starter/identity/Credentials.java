package com.example.starter.identity;

import java.time.Clock;
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
 * An account that is disabled, or stopped under the policy on refused sign-ins, is answered as one, and
 * the framework refuses it before the password is compared. The sign-in page says the same thing about
 * every refusal, so none of them tells whoever is guessing which addresses exist.
 *
 * <p>The {@link User} imported below is the framework's own, and not the persistent type of the same
 * name, which is never named in this file.
 */
@Component
public final class Credentials implements UserDetailsService {

    private static final String UNKNOWN = "No account signs in under that address";

    private final UserRepository repository;

    private final Clock clock;

    /**
     * Reads the accounts through the given repository.
     *
     * @param repository the accounts as the database holds them
     * @param clock      the clock every instant of the application is read from
     */
    public Credentials(UserRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Answers what the framework needs to decide whether somebody may sign in.
     *
     * @param email the address somebody is trying to sign in under, as they typed it
     * @return the account under that address, as the framework reads one
     * @throws UsernameNotFoundException when nobody signs in under that address
     */
    @Override
    public UserDetails loadUserByUsername(String email) {
        return this.repository.findByProfileEmail(UserProfile.canonical(email))
            .map(
                account -> User.withUsername(account.getProfile().email())
                    .password(account.getPasswordHash())
                    .roles(account.getAccess().role().name())
                    .disabled(!account.getAccess().enabled())
                    .accountLocked(account.getLockout().locks(this.clock.instant()))
                    .build()
            ).orElseThrow(() -> new UsernameNotFoundException(UNKNOWN));
    }
}
