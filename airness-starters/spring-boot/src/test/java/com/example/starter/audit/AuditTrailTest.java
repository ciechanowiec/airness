package com.example.starter.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.starter.DatabaseTest;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@DatabaseTest
class AuditTrailTest {

    private final AuditTrail trail;

    private final EntityManager entities;

    private final JdbcTemplate database;

    AuditTrailTest(AuditTrail trail, EntityManager entities, JdbcTemplate database) {
        this.trail = trail;
        this.entities = entities;
        this.database = database;
    }

    private static String target() {
        return "audited-%s@starter.test".formatted(UUID.randomUUID());
    }

    private AuditEvent written(String target) {
        String query = "SELECT event FROM AuditEvent event WHERE event.deed.target = :target";
        return this.entities.createQuery(query, AuditEvent.class).setParameter("target", target).getSingleResult();
    }

    @Test
    void recordsWhoDidWhatToWhomAndHowItTurnedOut() {
        String target = target();
        this.trail.write("someone@starter.test", AuditAction.ACCOUNT_CREATED, target, AuditOutcome.SUCCEEDED);
        assertEquals(
            new Deed("someone@starter.test", AuditAction.ACCOUNT_CREATED, target, AuditOutcome.SUCCEEDED),
            this.written(target).getDeed()
        );
    }

    @Test
    void keepsThePartOfAnOverlongActorThatFitsItsColumn() {
        String target = target();
        this.trail.write("x".repeat(AuditTrail.WIDTH * 2), AuditAction.SIGN_IN, target, AuditOutcome.REFUSED);
        assertEquals(AuditTrail.WIDTH, this.written(target).getDeed().actor().length());
    }

    @Test
    void refusesToChangeAnEntryOnceWritten() {
        String target = target();
        this.trail.write(AuditTrail.APPLICATION, AuditAction.SIGN_IN, target, AuditOutcome.REFUSED);
        assertThrows(
            DataAccessException.class,
            () -> this.database.update("UPDATE audit_event SET outcome = 'SUCCEEDED' WHERE target = ?", target)
        );
    }

    @Test
    void refusesToRemoveAnEntryOnceWritten() {
        String target = target();
        this.trail.write(AuditTrail.APPLICATION, AuditAction.SIGN_IN, target, AuditOutcome.REFUSED);
        assertThrows(
            DataAccessException.class,
            () -> this.database.update("DELETE FROM audit_event WHERE target = ?", target)
        );
    }
}
