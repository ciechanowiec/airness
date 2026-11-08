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
import org.springframework.jdbc.core.JdbcTemplate;

@DatabaseTest
class AccountPagesTest {

    private static final String CHOSEN = "a-new-fixture-not-a-secret";

    private final Users users;

    private final JdbcTemplate database;

    AccountPagesTest(Users users, JdbcTemplate database) {
        this.users = users;
        this.database = database;
    }

    private static String landing(HttpResponse<String> response) {
        return response.headers().firstValue(HttpHeaders.LOCATION).orElse("");
    }

    private static String form(String current, String chosen) {
        return "current=%s&chosen=%s".formatted(Caller.encoded(current), Caller.encoded(chosen));
    }

    @Test
    void drawsTheFormThatChangesThePassword(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "reader", Role.USER);
        assertTrue(Caller.signedIn(port, email, Accounts.SECRET).get("/account").body().contains("Current password"));
    }

    @Test
    void refusesACurrentPasswordThatIsNotTheReaders(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "forgetful", Role.USER);
        String page = Caller.signedIn(port, email, Accounts.SECRET)
            .post("/account/password", form("not-my-password", CHOSEN))
            .body();
        assertTrue(page.contains("This is not your current password."));
    }

    @Test
    void refusesAChosenPasswordThePolicyRefuses(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "hasty", Role.USER);
        String page = Caller.signedIn(port, email, Accounts.SECRET)
            .post("/account/password", form(Accounts.SECRET, "short"))
            .body();
        assertTrue(page.contains("A password is at least twelve characters long."));
    }

    @Test
    void changesThePasswordTheReaderSignsInWith(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "changed", Role.USER);
        HttpResponse<String> answer = Caller.signedIn(port, email, Accounts.SECRET)
            .post("/account/password", form(Accounts.SECRET, CHOSEN));
        assertAll(
            () -> assertTrue(landing(answer).endsWith("/account?notice=PASSWORD_CHANGED")),
            () -> assertTrue(landing(new Caller(port).signIn(email, CHOSEN)).endsWith("/")),
            () -> assertTrue(landing(new Caller(port).signIn(email, Accounts.SECRET)).endsWith("/login?failed=true"))
        );
    }

    @Test
    void endsTheOtherSessionsOfTheAccountAndKeepsTheOneThatChangedIt(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "rotated", Role.USER);
        Caller elsewhere = Caller.signedIn(port, email, Accounts.SECRET);
        Caller here = Caller.signedIn(port, email, Accounts.SECRET);
        here.post("/account/password", form(Accounts.SECRET, CHOSEN));
        assertAll(
            () -> assertTrue(landing(elsewhere.get("/tasks")).endsWith("/login?ended=true")),
            () -> assertEquals(HttpStatus.OK.value(), here.get("/tasks").statusCode())
        );
    }

    @Test
    void recordsAChangedPassword(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "audited", Role.USER);
        Caller.signedIn(port, email, Accounts.SECRET).post("/account/password", form(Accounts.SECRET, CHOSEN));
        assertEquals(
            1,
            this.database.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE actor = ? AND action = 'PASSWORD_CHANGED'", Long.class, email
            )
        );
    }
}
