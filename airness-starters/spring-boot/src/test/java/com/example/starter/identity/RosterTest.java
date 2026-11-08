package com.example.starter.identity;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.example.starter.Caller;
import com.example.starter.DatabaseTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Who the application counts as its last administrator who may sign in. Every test that gives the
 * application a second administrator stops that administrator again before it ends, because the suite
 * shares one database and the other tests read the first administrator as the last one.
 */
@DatabaseTest
class RosterTest {

    private final Users users;

    private final Roster roster;

    private final UserRepository repository;

    RosterTest(Users users, Roster roster, UserRepository repository) {
        this.users = users;
        this.roster = roster;
        this.repository = repository;
    }

    @AfterEach
    void actAsNobody() {
        SecurityContextHolder.clearContext();
    }

    private UUID reference(String email) {
        return this.repository.findByProfileEmail(email).orElseThrow().getReference();
    }

    @Test
    void countsNoAdministratorAsTheLastWhileAnotherMaySignIn() {
        UUID deputy = this.reference(Accounts.admitted(this.users, "deputy", Role.ADMIN));
        Caller.acting(Caller.ADMINISTRATOR, Role.ADMIN.getAuthority());
        try {
            assertFalse(this.roster.person(deputy).orElseThrow().sole());
        } finally {
            this.users.enabling(deputy, false);
        }
    }

    @Test
    void countsNoStoppedAdministratorAsTheLast() {
        UUID stopped = this.reference(Accounts.admitted(this.users, "stoppedDeputy", Role.ADMIN));
        Caller.acting(Caller.ADMINISTRATOR, Role.ADMIN.getAuthority());
        this.users.enabling(stopped, false);
        assertFalse(this.roster.person(stopped).orElseThrow().sole());
    }
}
