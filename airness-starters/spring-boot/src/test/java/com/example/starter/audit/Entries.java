package com.example.starter.audit;

import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The audit trail as a test reads it back. Every test that asserts an entry was written asks through
 * {@link #written}, naming the action as a constant of {@link AuditAction}, which is what
 * {@link AuditActionTest} reads the test sources for to prove no action goes unasserted.
 *
 * <p>Nothing here judges anything: it answers a count, and the tests assert.
 */
public final class Entries {

    private final JdbcTemplate database;

    /**
     * Reads the trail through the given template.
     *
     * @param database the database the application writes the trail into
     */
    public Entries(JdbcTemplate database) {
        this.database = database;
    }

    /**
     * Answers how many entries record the given action done to the given target with the given outcome.
     *
     * @param action  what was done
     * @param target  the account or task it was done to, as the trail names it
     * @param outcome how it turned out
     * @return how many such entries the trail holds
     */
    public long written(AuditAction action, String target, AuditOutcome outcome) {
        return Objects.requireNonNull(
            this.database.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE action = ? AND target = ? AND outcome = ?",
                Long.class, action.name(), target, outcome.name()
            )
        );
    }
}
