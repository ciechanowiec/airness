package com.example.starter.audit;

import org.springframework.data.repository.Repository;

/**
 * The audit trail as the database holds it. It declares a write and no other method, because an entry
 * is never read back into the application, changed or removed by it.
 */
@FunctionalInterface
public interface AuditEventRepository extends Repository<AuditEvent, Long> {

    /**
     * Writes the given entry.
     *
     * @param event the entry to write
     * @return the entry as written
     */
    AuditEvent save(AuditEvent event);
}
