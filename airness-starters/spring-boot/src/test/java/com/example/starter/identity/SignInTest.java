package com.example.starter.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.Caller;
import com.example.starter.DatabaseTest;
import com.example.starter.audit.AuditAction;
import com.example.starter.audit.AuditOutcome;
import com.example.starter.audit.Entries;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;

@DatabaseTest
class SignInTest {

    private static final String REFUSED = "/login?failed=true";

    private static final String WRONG = "not-the-password-at-all";

    private final Users users;

    private final JdbcTemplate database;

    private final Entries entries;

    SignInTest(Users users, JdbcTemplate database) {
        this.users = users;
        this.database = database;
        this.entries = new Entries(database);
    }

    private static String landing(HttpResponse<String> response) {
        return response.headers().firstValue(HttpHeaders.LOCATION).orElse("");
    }

    @Test
    void letsTheFirstAdministratorIn(@LocalServerPort int port) {
        String landed = landing(new Caller(port).signIn(Caller.ADMINISTRATOR, Caller.SECRET));
        assertTrue(landed.endsWith("/"), landed);
    }

    @Test
    void refusesAWrongPassword(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "wrong", Role.USER);
        assertTrue(landing(new Caller(port).signIn(email, WRONG)).endsWith(REFUSED));
    }

    @Test
    void refusesAnAddressNobodySignsInUnderTheSameWay(@LocalServerPort int port) {
        assertTrue(landing(new Caller(port).signIn(Accounts.fresh("nobody"), Accounts.SECRET)).endsWith(REFUSED));
    }

    @Test
    void letsAnAccountInUnderAnyCaseOfItsAddress(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "case", Role.USER);
        String landed = landing(new Caller(port).signIn(email.toUpperCase(Locale.ROOT), Accounts.SECRET));
        assertTrue(landed.endsWith("/"), landed);
    }

    @Test
    void stopsAnAccountAfterTooManyRefusedSignIns(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "guessed", Role.USER);
        Caller guesser = new Caller(port);
        IntStream.range(0, Lockout.LIMIT).forEach(_ -> guesser.signIn(email, WRONG));
        assertTrue(landing(new Caller(port).signIn(email, Accounts.SECRET)).endsWith(REFUSED));
    }

    @Test
    void recordsThatAnAccountWasStopped(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "stopped", Role.USER);
        Caller guesser = new Caller(port);
        IntStream.range(0, Lockout.LIMIT).forEach(_ -> guesser.signIn(email, WRONG));
        assertEquals(1, this.entries.written(AuditAction.ACCOUNT_LOCKED, email, AuditOutcome.SUCCEEDED));
    }

    @Test
    void startsTheCountAgainOnceASignInIsLetIn(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "recovered", Role.USER);
        Caller caller = new Caller(port);
        caller.signIn(email, WRONG);
        caller.signIn(email, Accounts.SECRET);
        assertEquals(
            0,
            this.database.queryForObject("SELECT failed_attempts FROM app_user WHERE email = ?", Integer.class, email)
        );
    }

    @Test
    void recordsASignInThatWasLetIn(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "recorded", Role.USER);
        new Caller(port).signIn(email, Accounts.SECRET);
        assertEquals(1, this.entries.written(AuditAction.SIGN_IN, email, AuditOutcome.SUCCEEDED));
    }

    @Test
    void recordsASignInThatWasRefused(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "refused", Role.USER);
        new Caller(port).signIn(email, WRONG);
        assertEquals(1, this.entries.written(AuditAction.SIGN_IN, email, AuditOutcome.REFUSED));
    }
}
