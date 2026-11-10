package com.example.starter.identity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.Caller;
import com.example.starter.DatabaseTest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/**
 * How a session ends: when its reader signs out, after which the cookie that carried it opens nothing
 * however often it is sent again, and when an administrator takes away what the reader signed in to do.
 */
@DatabaseTest
class SessionsTest {

    private static final String USERS = "/users";

    private final Users users;

    private final UserRepository repository;

    SessionsTest(Users users, UserRepository repository) {
        this.users = users;
        this.repository = repository;
    }

    private static String landing(HttpResponse<String> response) {
        return response.headers().firstValue(HttpHeaders.LOCATION).orElse("");
    }

    @Test
    void refusesTheSessionOfAReaderWhoSignedOutWhenItIsSentAgain(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "departed", Role.USER);
        Caller reader = Caller.signedIn(port, email, Accounts.SECRET);
        Caller replayed = reader.copied();
        int before = replayed.get("/tasks").statusCode();
        reader.post("/logout", "");
        HttpResponse<String> after = replayed.get("/tasks");
        assertAll(
            () -> assertEquals(HttpStatus.OK.value(), before),
            () -> assertTrue(landing(after).endsWith("/login"), landing(after))
        );
    }

    @Test
    void endsTheSessionsOfAnAdministratorWhoseRoleWasTakenAway(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "demoted", Role.ADMIN);
        Caller reader = Caller.signedIn(port, email, Accounts.SECRET);
        String reference = this.repository.findByProfileEmail(email).orElseThrow().getReference().toString();
        String form = "profile.fullName=Demoted&profile.email=%s&role=USER&password=".formatted(Caller.encoded(email));
        Caller.administrator(port).post("/users/%s".formatted(reference), form);
        HttpResponse<String> ended = reader.get(USERS);
        HttpResponse<String> again = Caller.signedIn(port, email, Accounts.SECRET).get(USERS);
        assertAll(
            () -> assertTrue(landing(ended).endsWith("/login?ended=true"), landing(ended)),
            () -> assertEquals(HttpStatus.FORBIDDEN.value(), again.statusCode())
        );
    }
}
