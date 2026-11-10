package com.example.starter.audit;

import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * What writes the audit trail. Every entry is stamped from the application clock and written in the
 * transaction of the change it records, so a change that is rolled back leaves no entry claiming it
 * happened.
 */
@Component
public final class AuditTrail {

    /**
     * Who is recorded as the actor of what the application does on its own, such as writing the first
     * administrator at startup or stopping an account under the policy on refused sign-ins.
     */
    public static final String APPLICATION = "application";

    /**
     * How wide an actor and a target may be, which is the width of the address an account signs in with.
     */
    static final int WIDTH = 180;

    private final AuditEventRepository events;

    private final Clock clock;

    /**
     * Writes the trail through the given repository, stamping each entry from the given clock.
     *
     * @param events the audit trail as the database holds it
     * @param clock  the clock every instant of the application is read from
     */
    public AuditTrail(AuditEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    /**
     * Records that somebody did something to an account or to a task.
     *
     * @param actor   who did it, by the address they sign in with, or {@link #APPLICATION}
     * @param action  what was done
     * @param target  the account it was done to, by the address that account signs in with, or the task it
     *                was done to, by the reference that task is known by
     * @param outcome how it turned out
     */
    public void write(String actor, AuditAction action, String target, AuditOutcome outcome) {
        this.events.save(
            new AuditEvent(this.clock.instant(), new Deed(fitted(actor), action, fitted(target), outcome))
        );
    }

    // What somebody typed into the sign-in form is as long as they made it, and the trail keeps the part
    // of it that fits the column rather than refusing to record the attempt at all.
    private static String fitted(String value) {
        return value.length() > WIDTH ? value.substring(0, WIDTH) : value;
    }
}
