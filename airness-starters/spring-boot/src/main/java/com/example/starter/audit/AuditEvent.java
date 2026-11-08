package com.example.starter.audit;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

/**
 * One entry of the audit trail: what was done, and when.
 *
 * <p>An entry is written once and never changed. Nothing in the application updates or removes one, and
 * the table refuses both at the database, so an entry stays what it was when it was written even against
 * a write that bypasses the application.
 *
 * <p>The class is not final and neither are its fields, because Hibernate subclasses a persistent type and
 * writes its fields after the constructor has run. Nothing in this codebase writes one.
 */
@Entity(name = "AuditEvent")
@Access(AccessType.FIELD)
@Table(name = "audit_event")
@Getter
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Embedded
    private Deed deed;

    /**
     * Leaves the entry for the persistence provider to fill in from a row it has read.
     */
    protected AuditEvent() {
        this.occurredAt = Instant.EPOCH;
        this.deed = new Deed("", AuditAction.SIGN_IN, "", AuditOutcome.REFUSED);
    }

    /**
     * Records what was done.
     *
     * @param occurredAt when it was done
     * @param deed       what was done
     */
    public AuditEvent(Instant occurredAt, Deed deed) {
        this.occurredAt = occurredAt;
        this.deed = deed;
    }
}
