package com.example.starter.identity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.Caller;
import com.example.starter.DatabaseTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.core.context.SecurityContextHolder;

// The reader's own row in the list of accounts. The test signs in as an administrator of its own, so the
// first administrator is not the last one while it runs, and stops that account again before it ends,
// because other tests rely on the first administrator being the only one who may sign in.
@DatabaseTest
class OwnAccountRowTest {

    private final Users users;

    private final UserRepository repository;

    OwnAccountRowTest(Users users, UserRepository repository) {
        this.users = users;
        this.repository = repository;
    }

    private UUID reference(String email) {
        return this.repository.findByProfileEmail(email).orElseThrow().getReference();
    }

    private static String control(UUID reference) {
        return "/users/%s/access".formatted(reference);
    }

    // The list sorts by name, so an account named Test aaa sits on the first page beside the first
    // administrator, whose row still offers the control.
    @Test
    void offersNoControlThatWouldStopTheReaderThemselves(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "aaa", Role.ADMIN);
        UUID own = this.reference(email);
        try {
            String page = Caller.signedIn(port, email, Accounts.SECRET).get("/users").body();
            assertAll(
                () -> assertTrue(page.contains(email)),
                () -> assertFalse(page.contains(control(own))),
                () -> assertTrue(page.contains(control(this.reference(Caller.ADMINISTRATOR))))
            );
        } finally {
            this.stop(own);
        }
    }

    private void stop(UUID reference) {
        Caller.acting(Caller.ADMINISTRATOR, Role.ADMIN.getAuthority());
        try {
            this.users.enabling(reference, false);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
