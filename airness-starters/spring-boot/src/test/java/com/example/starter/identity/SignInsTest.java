package com.example.starter.identity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.DatabaseTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The policy on refused sign-ins as the database counts it. Every attempt is taken before its password is
 * compared, so these tests take attempts directly, as attempts still being compared would hold them.
 */
@DatabaseTest
class SignInsTest {

    // How many attempts arrive at once in the test that races them, well beyond what the policy allows.
    private static final int CROWD = Lockout.LIMIT * 4;

    private static final long PATIENCE_SECONDS = 20;

    private final SignIns signIns;

    private final Users users;

    private final JdbcTemplate database;

    // The executor the container runs work on, so the attempts that race run on threads it manages.
    private final AsyncTaskExecutor crowd;

    SignInsTest(SignIns signIns, Users users, JdbcTemplate database, AsyncTaskExecutor crowd) {
        this.signIns = signIns;
        this.users = users;
        this.database = database;
        this.crowd = crowd;
    }

    private Instant lockedUntil(String email) {
        return Objects.requireNonNull(
            this.database.queryForObject(
                "SELECT locked_until FROM app_user WHERE email = ?", OffsetDateTime.class, email
            )
        ).toInstant();
    }

    private int counted(String email) {
        return Objects.requireNonNull(
            this.database.queryForObject("SELECT failed_attempts FROM app_user WHERE email = ?", Integer.class, email)
        );
    }

    private long taken(String email) {
        return IntStream.range(0, Lockout.LIMIT).filter(_ -> this.signIns.reserved(email)).count();
    }

    private String locked(String who) {
        String email = Accounts.admitted(this.users, who, Role.USER);
        this.taken(email);
        this.signIns.disproved(email);
        return email;
    }

    @SneakyThrows
    private long takenAtOnce(List<Callable<Boolean>> attempts) {
        List<Future<Boolean>> outcomes = attempts.stream().map(this.crowd::submit).toList();
        long taken = 0;
        for (Future<Boolean> outcome : outcomes) {
            taken += outcome.get(PATIENCE_SECONDS, TimeUnit.SECONDS) ? 1 : 0;
        }
        return taken;
    }

    @Test
    void takesNoMoreAttemptsThanThePolicyAllowsBeforeAnyIsRefused() {
        String email = Accounts.admitted(this.users, "hurried", Role.USER);
        assertAll(
            () -> assertEquals(Lockout.LIMIT, this.taken(email)),
            () -> assertFalse(this.signIns.reserved(email))
        );
    }

    @Test
    void takesNoMoreAttemptsThanThePolicyAllowsWhenTheyArriveAtOnce() {
        String email = Accounts.admitted(this.users, "crowded", Role.USER);
        List<Callable<Boolean>> attempts = IntStream.range(0, CROWD)
            .<Callable<Boolean>>mapToObj(_ -> () -> this.signIns.reserved(email))
            .toList();
        assertEquals(Lockout.LIMIT, this.takenAtOnce(attempts));
    }

    @Test
    void locksTheAccountOnceTheLastAttemptIsRefused() {
        assertTrue(this.lockedUntil(this.locked("spent")).isAfter(Instant.now()));
    }

    @Test
    void locksNothingWhileAttemptsAreLeft() {
        String email = Accounts.admitted(this.users, "spared", Role.USER);
        this.signIns.reserved(email);
        this.signIns.disproved(email);
        assertTrue(this.signIns.reserved(email));
    }

    @Test
    void neitherCountsNorExtendsAnAttemptWhileLocked() {
        String email = this.locked("patient");
        Instant locked = this.lockedUntil(email);
        this.signIns.refused(email);
        assertAll(
            () -> assertFalse(this.signIns.reserved(email)),
            () -> assertEquals(locked, this.lockedUntil(email)),
            () -> assertEquals(0, this.counted(email))
        );
    }

    @Test
    void liftsTheLockOnceItsSpanHasPassed() {
        String email = this.locked("lifted");
        this.database.update(
            "UPDATE app_user SET locked_until = ? WHERE email = ?", Timestamp.from(Instant.EPOCH), email
        );
        assertTrue(this.signIns.reserved(email));
    }

    @Test
    void givesBackEveryAttemptOnceAPasswordIsProved() {
        String email = Accounts.admitted(this.users, "proved", Role.USER);
        this.taken(email);
        this.signIns.proved(email);
        assertEquals(0, this.counted(email));
    }
}
