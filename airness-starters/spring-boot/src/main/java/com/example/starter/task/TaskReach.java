package com.example.starter.task;

import com.example.starter.audit.AuditAction;
import com.example.starter.audit.AuditOutcome;
import com.example.starter.audit.AuditTrail;
import com.example.starter.identity.Owners;
import com.example.starter.identity.Reader;
import com.example.starter.identity.Role;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Which tasks the reader may see, how a task is shown to them, and what the audit trail records when they
 * change a task beyond their own. An administrator sees every task and whose it is, and anybody else sees
 * only their own, whose name they know, so the names are read for administrators alone. Changing a task
 * somebody else keeps is the privilege of an administrator, so every such change is recorded, while a
 * change to one's own task is not.
 */
@Component
public final class TaskReach {

    private final Owners owners;

    private final AuditTrail trail;

    private final Clock clock;

    /**
     * Reads the reader's reach through the given accounts.
     *
     * @param owners the accounts as the tasks they keep read them
     * @param trail  what records every change to a task somebody else keeps
     * @param clock  the clock the day a task is shown on is read from
     */
    public TaskReach(Owners owners, AuditTrail trail, Clock clock) {
        this.owners = owners;
        this.trail = trail;
        this.clock = clock;
    }

    /**
     * Answers the tasks the reader may see: every task for an administrator, and their own for anybody else.
     *
     * @return the narrowing every read of the tasks starts from
     */
    Specification<Task> visible() {
        return Reader.holds(Role.ADMIN) ? Specification.unrestricted() : TaskCriteria.ownedBy(this.owners.reader());
    }

    /**
     * Answers how the given tasks are shown to the reader, their owners' names read in one query.
     *
     * @param found the tasks as the database holds them
     * @return the tasks as the pages show them, in the same order
     */
    List<TaskView> views(List<Task> found) {
        Map<Long, String> names = this.names(found.stream().map(Task::getOwnerId).collect(Collectors.toSet()));
        LocalDate today = LocalDate.now(this.clock);
        return found.stream().map(task -> TaskView.of(task, today, names.getOrDefault(task.getOwnerId(), ""))).toList();
    }

    /**
     * Answers how the given task is shown to the reader.
     *
     * @param task the task as the database holds it
     * @return the task as the pages show it
     */
    TaskView view(Task task) {
        return this.views(List.of(task)).getFirst();
    }

    /**
     * Records that the reader did the given thing to the given task, where somebody else keeps it.
     *
     * @param task   the task as it stood before the change
     * @param action what the reader did to it
     */
    void recorded(Task task, AuditAction action) {
        if (task.getOwnerId() != this.owners.reader()) {
            this.trail.write(
                this.owners.readerReference().toString(), action, task.getReference().toString(),
                AuditOutcome.SUCCEEDED
            );
        }
    }

    private Map<Long, String> names(Set<Long> owners) {
        return Reader.holds(Role.ADMIN) ? this.owners.names(owners) : Map.of();
    }
}
