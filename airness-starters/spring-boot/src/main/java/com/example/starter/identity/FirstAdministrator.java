package com.example.starter.identity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * The administrator an application starting against an empty database is signed in to with.
 *
 * <p>There is no self-registration, so without this a fresh database would hold no account and the
 * application would be unreachable. The address and the password are read from configuration, so a
 * deployment that supplies neither fails at startup rather than falling back to a credential shipped
 * inside the artifact, and one whose password breaks the policy fails there too.
 *
 * <p>It runs at every start and writes at most once, and first among the runners, so anything else that
 * admits somebody at startup finds the administrator already written.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class FirstAdministrator implements ApplicationRunner {

    private static final String NAME = "Administrator";

    private static final String WRITTEN = "The application had no account, so the first administrator {} was written";

    private final Users users;

    private final String email;

    private final String password;

    /**
     * Writes the first administrator through the given application layer.
     *
     * @param users    the accounts of the application
     * @param email    the address the first administrator signs in with
     * @param password the password the first administrator signs in with
     */
    public FirstAdministrator(
        Users users,
        @Value("${app.administrator.email}") String email,
        @Value("${app.administrator.password}") String password
    ) {
        this.users = users;
        this.email = email;
        this.password = password;
    }

    /**
     * Writes the administrator if there is nobody to sign in as, and does nothing otherwise.
     *
     * @param arguments what the command line passed to the container
     */
    @Override
    public void run(ApplicationArguments arguments) {
        this.users.bootstrap(new UserProfile(this.email, NAME), this.password)
            .ifPresent(administrator -> log.atInfo().log(WRITTEN, administrator));
    }
}
