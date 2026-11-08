package com.example.starter.home;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.Caller;
import com.example.starter.DatabaseTest;
import com.example.starter.identity.Accounts;
import com.example.starter.identity.Role;
import com.example.starter.identity.Users;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

@DatabaseTest
class HomePagesTest {

    private final Users users;

    HomePagesTest(Users users) {
        this.users = users;
    }

    @Test
    void introducesTheApplicationToSomebodyWhoHasNotSignedIn(@LocalServerPort int port) {
        assertTrue(new Caller(port).get("/").body().contains("Keep your tasks in one place"));
    }

    @Test
    void showsASignedInReaderHowTheirTasksStand(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "home", Role.USER);
        assertTrue(Caller.signedIn(port, email, Accounts.SECRET).get("/").body().contains("/tasks?status=OPEN"));
    }
}
