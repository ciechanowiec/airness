package com.example.starter.identity;

import java.time.Clock;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * What the application knows about somebody trying to sign in, answered to the part of the framework
 * that checks a password, before the password is compared and again once it has matched.
 *
 * <p>This is the one reader of accounts that holds the repository rather than the application layer.
 * It has to: it runs while the caller holds no role, because deciding who they are is what it is for.
 * An account that is disabled, or stopped under the policy on refused sign-ins, is answered as one, and
 * the framework refuses it before the password is compared. The sign-in page says the same thing about
 * every refusal, so none of them tells whoever is guessing which addresses exist.
 *
 * <p>Comparing a password takes a quarter of a second on purpose, and sign-ins arrive at once. Every one
 * of them read the account before the comparison, while it still stood, so a sign-in whose password
 * matched is asked about the account again, as the database holds it once the comparison is over. One
 * that was stopped meanwhile, by refused sign-ins racing it or by an administrator, is refused then.
 *
 * <p>It is not a bean of its own: the provider the filter chain signs in through is built around it, so
 * the framework never builds a second provider from it that would skip the second question.
 *
 * <p>The {@link User} imported below is the framework's own, and not the persistent type of the same
 * name, which is never named in this file.
 */
public final class Credentials implements UserDetailsService, UserDetailsChecker {

    private static final String UNKNOWN = "No account signs in under that address";

    private static final UserDetailsChecker STANDING = new AccountStatusUserDetailsChecker();

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

    /**
     * Refuses an account whose password matched but which may no longer sign in, read afresh rather than
     * from the details the comparison began with.
     *
     * @param matched the account as it was read before its password was compared
     * @throws org.springframework.security.authentication.DisabledException when the account was disabled
     *                                                                       meanwhile
     * @throws org.springframework.security.authentication.LockedException   when the account was stopped
     *                                                                       under the policy on refused
     *                                                                       sign-ins meanwhile
     */
    @Override
    public void check(UserDetails matched) {
        STANDING.check(this.loadUserByUsername(matched.getUsername()));
    }
}
