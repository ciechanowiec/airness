package com.example.starter.audit;

import org.springframework.data.repository.Repository;

/**
 * The audit trail as the database holds it. It declares a write and no other method, because an entry
 * is never read back into the application, changed or removed by it.
 */
@FunctionalInterface
public interface AuditEventRepository extends Repository<AuditEvent, Long> {

    /**
     * Writes the given entry. Nothing is answered, because the trail writes an entry and never reads it back.
     *
     * @param event the entry to write
     */
    void save(AuditEvent event);
}
