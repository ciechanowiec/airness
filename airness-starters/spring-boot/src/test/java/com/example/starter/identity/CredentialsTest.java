package com.example.starter.identity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.starter.Caller;
import com.example.starter.DatabaseTest;
import java.time.Clock;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The second question a sign-in is asked once its password matched, put with the account changed between
 * the reading the comparison began with and the question, which is what a comparison running while other
 * sign-ins or an administrator change the account amounts to.
 */
@DatabaseTest
class CredentialsTest {

    private final Users users;

    private final UserRepository repository;

    private final SignIns signIns;

    private final Credentials credentials;

    CredentialsTest(Users users, UserRepository repository, SignIns signIns, Clock clock) {
        this.users = users;
        this.repository = repository;
        this.signIns = signIns;
        this.credentials = new Credentials(repository, clock);
    }

    @AfterEach
    void actAsNobody() {
        SecurityContextHolder.clearContext();
    }

    private UUID reference(String email) {
        return this.repository.findByProfileEmail(email).orElseThrow().getReference();
    }

    @Test
    void refusesAnAccountStoppedAfterItsDetailsWereRead() {
        String email = Accounts.admitted(this.users, "overtaken", Role.USER);
        UserDetails read = this.credentials.loadUserByUsername(email);
        IntStream.range(0, Lockout.LIMIT).forEach(_ -> this.signIns.refused(email));
        assertThrows(LockedException.class, () -> this.credentials.check(read));
    }

    @Test
    void refusesAnAccountDisabledAfterItsDetailsWereRead() {
        String email = Accounts.admitted(this.users, "withdrawn", Role.USER);
        UserDetails read = this.credentials.loadUserByUsername(email);
        Caller.acting(Caller.ADMINISTRATOR, Role.ADMIN.getAuthority());
        this.users.enabling(this.reference(email), false);
        assertThrows(DisabledException.class, () -> this.credentials.check(read));
    }

    @Test
    void passesAnAccountThatStandsAsItWasRead() {
        String email = Accounts.admitted(this.users, "steady", Role.USER);
        UserDetails read = this.credentials.loadUserByUsername(email);
        assertDoesNotThrow(() -> this.credentials.check(read));
    }
}
