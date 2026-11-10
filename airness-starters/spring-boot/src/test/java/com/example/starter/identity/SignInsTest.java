package com.example.starter.identity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.starter.Caller;
import com.example.starter.DatabaseTest;
import com.example.starter.audit.AuditAction;
import com.example.starter.audit.AuditOutcome;
import com.example.starter.audit.Entries;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Attempts to prove a password that arrive at once, refused sign-ins and current passwords typed to change
 * one, against the database a deployment runs. Each test holds the account's row itself until every attempt
 * has read what it reads and is waiting to write, and only then lets them go, so the attempts are made to
 * overlap on every run rather than whenever the machine happens to interleave them: a count read before
 * another attempt wrote would be written back over it.
 */
@DatabaseTest
class SignInsTest {

    // Longer than any refusal takes once the row is let go, and shorter than the transaction timeout of a
    // refusal, which would otherwise end the wait with an error of its own.
    private static final long WAIT_SECONDS = 9;

    private static final String WRONG = "not-the-current-password";

    private static final String CHOSEN = "a-new-fixture-not-a-secret";

    // What another session wrote as the password while a change was being made. It is no hash any password
    // matches, which does not matter: only whether it was kept is asked.
    private static final String REPLACED = "replaced-meanwhile";

    private final Users users;

    private final SignIns signIns;

    // The executor the container runs work on, whose threads make the refusals that arrive at once.
    private final AsyncTaskExecutor callers;

    private final JdbcTemplate database;

    private final TransactionTemplate holding;

    private final Entries entries;

    SignInsTest(
        Users users, SignIns signIns, AsyncTaskExecutor callers, JdbcTemplate database,
        PlatformTransactionManager transactions
    ) {
        this.users = users;
        this.signIns = signIns;
        this.callers = callers;
        this.database = database;
        this.holding = new TransactionTemplate(transactions);
        this.entries = new Entries(database);
    }

    private int failedAttempts(String email) {
        return Objects.requireNonNull(
            this.database.queryForObject("SELECT failed_attempts FROM app_user WHERE email = ?", Integer.class, email)
        );
    }

    // How many requests wait for a lock another transaction holds. Unlike the activity view, this one is
    // read afresh on every query, inside a transaction too.
    private long waiting() {
        return Objects.requireNonNull(
            this.database.queryForObject("SELECT COUNT(*) FROM pg_locks WHERE NOT granted", Long.class)
        );
    }

    // Holds the account's row until every attempt waits on it, does whatever the test says happens
    // meanwhile, and lets them all go when the holding transaction commits. Answers what each attempt
    // answered.
    @SneakyThrows
    private <T> List<T> heldThrough(String email, List<Callable<T>> attempts, List<Runnable> meanwhile) {
        List<Future<T>> started = this.holding.execute(_ -> this.whileHeld(email, attempts, meanwhile));
        List<T> answers = new ArrayList<>();
        for (Future<T> attempt : Objects.requireNonNull(started)) {
            answers.add(attempt.get(WAIT_SECONDS, TimeUnit.SECONDS));
        }
        return answers;
    }

    // Runs inside the transaction that holds the row, which commits once this answers and so lets every
    // attempt waiting on it go at once.
    private <T> List<Future<T>> whileHeld(String email, List<Callable<T>> attempts, List<Runnable> meanwhile) {
        this.database.queryForObject("SELECT id FROM app_user WHERE email = ? FOR UPDATE", Long.class, email);
        List<Future<T>> started = attempts.stream().map(this.callers::submit).toList();
        while (this.waiting() < attempts.size()) {
            Thread.onSpinWait();
        }
        meanwhile.forEach(Runnable::run);
        return started;
    }

    private void refusedAtOnce(String email, int count) {
        this.heldThrough(email, Collections.nCopies(count, this.refusal(email)), List.of());
    }

    private Callable<Boolean> refusal(String email) {
        return () -> {
            this.signIns.refused(email);
            return true;
        };
    }

    // Who is signed in is held per thread, so each proof signs its own thread in as the reader and out again.
    private Callable<Boolean> proof(String email, String password) {
        return () -> {
            Caller.acting(email, Role.USER.getAuthority());
            try {
                return this.users.proves(password);
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    // A password change as the reader, proved by their current password, answering whether it was made.
    private Callable<Boolean> change(String email) {
        return () -> {
            Caller.acting(email, Role.USER.getAuthority());
            try {
                this.users.changePassword(Accounts.SECRET, CHOSEN);
                return true;
            } catch (IllegalStateException _) {
                return false;
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    private String passwordHash(String email) {
        return Objects.requireNonNull(
            this.database.queryForObject("SELECT password_hash FROM app_user WHERE email = ?", String.class, email)
        );
    }

    // Writes another password as a second session would, inside the transaction holding the account.
    private void replace(String email) {
        this.database.update("UPDATE app_user SET password_hash = ? WHERE email = ?", REPLACED, email);
    }

    // Stops the account as an administrator or a racing refusal would, inside the transaction holding it.
    private void stop(String email) {
        this.database.update("UPDATE app_user SET locked_until = now() + interval '1 hour' WHERE email = ?", email);
    }

    @Test
    void countsEveryOneOfRefusedSignInsMadeAtOnce() {
        String email = Accounts.admitted(this.users, "crowded", Role.USER);
        this.refusedAtOnce(email, Lockout.LIMIT - 1);
        assertEquals(Lockout.LIMIT - 1, this.failedAttempts(email));
    }

    @Test
    void stopsAnAccountOnceRefusedSignInsMadeAtOnceReachTheLimit() {
        String email = Accounts.admitted(this.users, "stampeded", Role.USER);
        this.refusedAtOnce(email, Lockout.LIMIT);
        assertAll(
            () -> assertEquals(1, this.entries.written(AuditAction.ACCOUNT_LOCKED, email, AuditOutcome.SUCCEEDED)),
            () -> assertEquals(Lockout.LIMIT, this.entries.written(AuditAction.SIGN_IN, email, AuditOutcome.REFUSED))
        );
    }

    @Test
    void countsEveryOneOfWrongCurrentPasswordsTypedAtOnce() {
        String email = Accounts.admitted(this.users, "hurried", Role.USER);
        this.heldThrough(email, Collections.nCopies(Lockout.LIMIT - 1, this.proof(email, WRONG)), List.of());
        assertEquals(Lockout.LIMIT - 1, this.failedAttempts(email));
    }

    @Test
    void refusesTheRightCurrentPasswordOfAnAccountStoppedWhileItWasCompared() {
        String email = Accounts.admitted(this.users, "overtaken", Role.USER);
        List<Boolean> proved = this.heldThrough(
            email, List.of(this.proof(email, Accounts.SECRET)), List.of(() -> this.stop(email))
        );
        assertEquals(List.of(false), proved);
    }

    @Test
    void countsAWrongCurrentPasswordAlthoughTheChangeItWasMeantToProveIsRefused() {
        String email = Accounts.admitted(this.users, "undone", Role.USER);
        Caller.acting(email, Role.USER.getAuthority());
        try {
            assertThrows(IllegalStateException.class, () -> this.users.changePassword(WRONG, Accounts.SECRET));
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertAll(
            () -> assertEquals(1, this.failedAttempts(email)),
            () -> assertEquals(1, this.entries.written(AuditAction.PASSWORD_CHANGED, email, AuditOutcome.REFUSED))
        );
    }

    @Test
    void keepsAPasswordAnotherSessionChangedWhileAChangeProvedTheOldOne() {
        String email = Accounts.admitted(this.users, "outpaced", Role.USER);
        List<Boolean> changed = this.heldThrough(
            email, List.of(this.change(email)),
            List.of(() -> this.replace(email))
        );
        assertAll(
            () -> assertEquals(List.of(false), changed),
            () -> assertEquals(REPLACED, this.passwordHash(email)),
            () -> assertEquals(1, this.entries.written(AuditAction.PASSWORD_CHANGED, email, AuditOutcome.REFUSED))
        );
    }
}
