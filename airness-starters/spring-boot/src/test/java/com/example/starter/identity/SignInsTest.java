package com.example.starter.identity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.starter.DatabaseTest;
import com.example.starter.audit.AuditAction;
import com.example.starter.audit.AuditOutcome;
import com.example.starter.audit.Entries;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Refused sign-ins that arrive at once, against the database a deployment runs. Each test holds the
 * account's row itself until every refusal has read what it reads and is waiting to write, and only then
 * lets them go, so the refusals are made to overlap on every run rather than whenever the machine happens to
 * interleave them: a count read before another refusal wrote would be written back over it.
 */
@DatabaseTest
class SignInsTest {

    // Longer than any refusal takes once the row is let go, and shorter than the transaction timeout of a
    // refusal, which would otherwise end the wait with an error of its own.
    private static final long WAIT_SECONDS = 9;

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

    @SneakyThrows
    private void refusedAtOnce(String email, int count) {
        List<Future<?>> refusals = this.holding.execute(_ -> this.whileHeld(email, count));
        for (Future<?> refusal : Objects.requireNonNull(refusals)) {
            refusal.get(WAIT_SECONDS, TimeUnit.SECONDS);
        }
    }

    // Runs inside the transaction that holds the row, which commits once this answers and so lets every
    // refusal waiting on it go at once.
    private List<Future<?>> whileHeld(String email, int count) {
        this.database.queryForObject("SELECT id FROM app_user WHERE email = ? FOR UPDATE", Long.class, email);
        List<Future<?>> refusals = IntStream.range(0, count)
            .<Future<?>>mapToObj(_ -> this.callers.submit(() -> this.signIns.refused(email)))
            .toList();
        while (this.waiting() < count) {
            Thread.onSpinWait();
        }
        return refusals;
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
}
