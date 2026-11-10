package com.example.starter.identity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.Caller;
import com.example.starter.DatabaseTest;
import com.example.starter.audit.AuditAction;
import com.example.starter.audit.AuditOutcome;
import com.example.starter.audit.Entries;
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

    private static final String PASSWORD = "/account/password";

    private static final String WRONG = "not-my-password";

    private static final String NOT_THEIRS = "This is not your current password.";

    // A letter BCrypt reads as two bytes, so thirty-seven of them are seventy-four bytes held in fewer
    // characters than the limit.
    private static final String TWO_BYTES = "ą";

    private static final int OVER_IN_TWO_BYTE_LETTERS = 37;

    private final Users users;

    private final JdbcTemplate database;

    private final Entries entries;

    AccountPagesTest(Users users, JdbcTemplate database) {
        this.users = users;
        this.database = database;
        this.entries = new Entries(database);
    }

    private int failedAttempts(String email) {
        return Objects.requireNonNull(
            this.database.queryForObject("SELECT failed_attempts FROM app_user WHERE email = ?", Integer.class, email)
        );
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
            .post(PASSWORD, form(WRONG, CHOSEN))
            .body();
        assertTrue(page.contains(NOT_THEIRS));
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
        assertEquals(1, this.entries.written(AuditAction.PASSWORD_CHANGED, email, AuditOutcome.SUCCEEDED));
    }

    @Test
    void refusesAChosenPasswordLongerInBytesThanTheEncoderReadsOnThePage(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "lengthy", Role.USER);
        HttpResponse<String> answer = Caller.signedIn(port, email, Accounts.SECRET)
            .post(PASSWORD, form(Accounts.SECRET, TWO_BYTES.repeat(OVER_IN_TWO_BYTE_LETTERS)));
        assertAll(
            () -> assertEquals(HttpStatus.OK.value(), answer.statusCode()),
            () -> assertTrue(answer.body().contains("A password is at most seventy-two bytes"))
        );
    }

    @Test
    void countsACurrentPasswordThatIsNotTheReadersAgainstTheirAccount(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "guessing", Role.USER);
        Caller.signedIn(port, email, Accounts.SECRET).post(PASSWORD, form(WRONG, CHOSEN));
        assertEquals(1, this.failedAttempts(email));
    }

    @Test
    void recordsACurrentPasswordThatIsNotTheReadersAsARefusedChange(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "traced", Role.USER);
        Caller.signedIn(port, email, Accounts.SECRET).post(PASSWORD, form(WRONG, CHOSEN));
        assertEquals(1, this.entries.written(AuditAction.PASSWORD_CHANGED, email, AuditOutcome.REFUSED));
    }

    @Test
    void refusesEvenTheRightCurrentPasswordOnceWrongOnesStoppedTheAccount(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "persistent", Role.USER);
        Caller reader = Caller.signedIn(port, email, Accounts.SECRET);
        IntStream.range(0, Lockout.LIMIT).forEach(_ -> reader.post(PASSWORD, form(WRONG, CHOSEN)));
        String page = reader.post(PASSWORD, form(Accounts.SECRET, CHOSEN)).body();
        assertAll(
            () -> assertTrue(page.contains(NOT_THEIRS)),
            () -> assertEquals(0, this.entries.written(AuditAction.PASSWORD_CHANGED, email, AuditOutcome.SUCCEEDED))
        );
    }
}
