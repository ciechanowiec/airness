package com.example.starter.task;

import com.example.starter.TransactionTimeout;
import com.example.starter.audit.AuditAction;
import com.example.starter.identity.Owners;
import com.example.starter.media.MediaObject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The rows that attach stored files to tasks, each written in a transaction of its own, because the file
 * between them is stored and rendered outside any transaction. Every one starts from the tasks the reader may
 * see, so a file is attached to, listed for or taken from a task the reader may see and no other.
 *
 * <p>The class is not final because the container wraps it in a proxy, which is a subclass.
 */
@Component
public class AttachmentRecords {

    private final TaskAttachmentRepository attachments;

    private final TaskRepository tasks;

    private final TaskReach reach;

    private final Owners owners;

    /**
     * Reads and writes the attachments through the given repositories.
     *
     * @param attachments the files attached to tasks
     * @param tasks       the tasks
     * @param reach       which tasks the reader may see, and what the audit trail records
     * @param owners      the accounts as the tasks they keep read them
     */
    public AttachmentRecords(
        TaskAttachmentRepository attachments,
        TaskRepository tasks,
        TaskReach reach,
        Owners owners
    ) {
        this.attachments = attachments;
        this.tasks = tasks;
        this.reach = reach;
        this.owners = owners;
    }

    /**
     * Answers where a file attached to the given task goes, and whether the task has room for one more.
     *
     * @param reference the reference the task is known by outside the database
     * @return the task, the reader attaching to it, and whether it is full
     * @throws IllegalStateException when the reader may not see a task under that reference
     */
    @Transactional(readOnly = true, timeout = TransactionTimeout.SECONDS)
    public Room room(UUID reference) {
        Task task = this.found(reference);
        boolean full = this.attachments.countByTaskId(task.getId()) >= TaskLimits.ATTACHMENTS;
        return new Room(task.getId(), this.owners.reader(), full);
    }

    /**
     * Attaches a stored file to the task the given room names.
     *
     * @param room  where the file goes
     * @param media the stored file
     */
    @Transactional(readOnly = false, timeout = TransactionTimeout.SECONDS)
    public void linked(Room room, MediaObject media) {
        this.attachments.save(new TaskAttachment(room.taskId(), media.getId(), room.actor(), media.getCreatedAt()));
        this.tasks.findById(room.taskId()).ifPresent(task -> this.reach.recorded(task, AuditAction.ATTACHMENT_ADDED));
    }

    /**
     * Takes a stored file from a task.
     *
     * @param reference the reference the task is known by outside the database
     * @param media     the reference the stored file is known by
     * @return the identifier of the stored file, which is no longer attached, or nothing where the task holds
     *         no such file
     * @throws IllegalStateException when the reader may not see a task under that reference
     */
    @Transactional(readOnly = false, timeout = TransactionTimeout.SECONDS)
    public Optional<Long> unlinked(UUID reference, UUID media) {
        Task task = this.found(reference);
        Optional<TaskAttachment> held = this.attachments.held(task.getId(), media);
        held.ifPresent(attachment -> this.removed(task, attachment));
        return held.map(TaskAttachment::getMediaId);
    }

    /**
     * Answers the stored files attached to the given task, in the order they were attached.
     *
     * @param reference the reference the task is known by outside the database
     * @return the identifiers of the files, or none where the reader may not see a task under that reference
     */
    @Transactional(readOnly = true, timeout = TransactionTimeout.SECONDS)
    public List<Long> held(UUID reference) {
        return this.seen(reference)
            .map(
                task -> this.attachments.findByTaskIdOrderByAddedAtAscIdAsc(
                    task.getId(), Limit.of(TaskLimits.ATTACHMENTS)
                )
            ).orElse(List.of())
            .stream()
            .map(TaskAttachment::getMediaId)
            .toList();
    }

    private void removed(Task task, TaskAttachment attachment) {
        this.attachments.delete(attachment);
        this.reach.recorded(task, AuditAction.ATTACHMENT_REMOVED);
    }

    private Optional<Task> seen(UUID reference) {
        return this.tasks.findOne(this.reach.visible().and(TaskCriteria.referenced(reference)));
    }

    private Task found(UUID reference) {
        return this.seen(reference).orElseThrow(
            () -> new IllegalStateException("No task the reader may see carries the reference %s".formatted(reference))
        );
    }

    /**
     * Where a file attached to a task goes.
     *
     * @param taskId the identifier of the task
     * @param actor  the identifier of the account attaching the file
     * @param full   whether the task already holds as many files as a task may
     */
    public record Room(long taskId, long actor, boolean full) {
    }
}
