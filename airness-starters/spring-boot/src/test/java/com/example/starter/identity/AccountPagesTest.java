package com.example.starter.identity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.Caller;
import com.example.starter.DatabaseTest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@DatabaseTest
class AccountPagesTest {

    private static final String CHOSEN = "a-new-fixture-not-a-secret";

    private static final String WRONG = "not-my-password";

    private static final String PASSWORD = "/account/password";

    // The entries of the trail about the given account's password, which it is named in by its reference.
    private static final String RECORDED = """
        SELECT COUNT(*) FROM audit_event JOIN app_user ON audit_event.target = CAST(app_user.reference AS VARCHAR)
        WHERE app_user.email = ? AND audit_event.actor = audit_event.target
        AND audit_event.action = 'PASSWORD_CHANGED' AND audit_event.outcome = ?""";

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

    private long recorded(String email, String outcome) {
        return Objects.requireNonNull(this.database.queryForObject(RECORDED, Long.class, email, outcome));
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
            .post(PASSWORD, form(WRONG, CHOSEN))
            .body();
        assertTrue(page.contains("This is not your current password."));
    }

    @Test
    void locksTheAccountOnceTooManyWrongCurrentPasswordsAreTyped(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "guessing", Role.USER);
        Caller holder = Caller.signedIn(port, email, Accounts.SECRET);
        IntStream.range(0, Lockout.LIMIT).forEach(_ -> holder.post(PASSWORD, form(WRONG, CHOSEN)));
        String page = holder.post(PASSWORD, form(Accounts.SECRET, CHOSEN)).body();
        assertAll(
            () -> assertTrue(page.contains("Too many wrong passwords were tried."), page),
            () -> assertTrue(landing(new Caller(port).signIn(email, Accounts.SECRET)).endsWith("/login?failed=true"))
        );
    }

    @Test
    void recordsAWrongCurrentPassword(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "recordedGuess", Role.USER);
        Caller.signedIn(port, email, Accounts.SECRET).post(PASSWORD, form(WRONG, CHOSEN));
        assertEquals(1, this.recorded(email, "REFUSED"));
    }

    @Test
    void sendsTheLanguageSwitchOfARefusedFormToAnAddressThatCanBeRead(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "switching", Role.USER);
        String page = Caller.signedIn(port, email, Accounts.SECRET).post(PASSWORD, form(WRONG, CHOSEN)).body();
        assertAll(
            () -> assertTrue(page.contains("name=\"back\" value=\"/\""), page),
            () -> assertFalse(page.contains("value=\"/account/password\""), page)
        );
    }

    @Test
    void refusesAChosenPasswordThePolicyRefuses(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "hasty", Role.USER);
        String page = Caller.signedIn(port, email, Accounts.SECRET)
            .post(PASSWORD, form(Accounts.SECRET, "short"))
            .body();
        assertTrue(page.contains("A password is at least twelve characters long."));
    }

    @Test
    void changesThePasswordTheReaderSignsInWith(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "changed", Role.USER);
        HttpResponse<String> answer = Caller.signedIn(port, email, Accounts.SECRET)
            .post(PASSWORD, form(Accounts.SECRET, CHOSEN));
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
        here.post(PASSWORD, form(Accounts.SECRET, CHOSEN));
        assertAll(
            () -> assertTrue(landing(elsewhere.get("/tasks")).endsWith("/login?ended=true")),
            () -> assertEquals(HttpStatus.OK.value(), here.get("/tasks").statusCode())
        );
    }

    @Test
    void recordsAChangedPassword(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "audited", Role.USER);
        Caller.signedIn(port, email, Accounts.SECRET).post(PASSWORD, form(Accounts.SECRET, CHOSEN));
        assertEquals(1, this.recorded(email, "SUCCEEDED"));
    }
}
