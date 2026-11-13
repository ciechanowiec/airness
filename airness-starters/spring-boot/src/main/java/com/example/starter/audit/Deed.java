package com.example.starter.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * What an entry of the audit trail says was done: who did what to which account or task, and how it
 * turned out. {@link AuditTrail#write} says how the actor and the target are named.
 *
 * @param actor   who did it
 * @param action  what was done
 * @param target  the account or the task it was done to
 * @param outcome how it turned out
 */
@Embeddable
public record Deed(
    @Column(name = "actor", nullable = false, updatable = false, length = AuditTrail.WIDTH) String actor,

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false)
    AuditAction action,

    @Column(name = "target", nullable = false, updatable = false, length = AuditTrail.WIDTH) String target,

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, updatable = false)
    AuditOutcome outcome
) {
}
