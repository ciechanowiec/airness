package com.example.starter.task;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

/**
 * A stored file attached to a task. It names the task and the file by their identifiers rather than holding
 * either, so reading the attachments of a page never loads a task or a file it does not draw.
 *
 * <p>The class is not final and neither are its fields, because Hibernate subclasses a persistent type and
 * writes its fields after the constructor has run. Nothing in this codebase writes one.
 */
@Entity(name = "TaskAttachment")
@Access(AccessType.FIELD)
@Table(name = "task_attachment")
@Getter
public class TaskAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private long id;

    @Column(name = "task_id", nullable = false, updatable = false)
    private long taskId;

    @Column(name = "media_id", nullable = false, unique = true, updatable = false)
    private long mediaId;

    @Column(name = "added_by", nullable = false, updatable = false)
    private long addedBy;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    /**
     * Leaves the attachment for the persistence provider to fill in from a row it has read.
     */
    protected TaskAttachment() {
        this.addedAt = Instant.EPOCH;
    }

    /**
     * Attaches a stored file to a task.
     *
     * @param taskId  the identifier of the task
     * @param mediaId the identifier of the stored file
     * @param addedBy the identifier of the account that attached it
     * @param addedAt when it was attached
     */
    public TaskAttachment(long taskId, long mediaId, long addedBy, Instant addedAt) {
        this.taskId = taskId;
        this.mediaId = mediaId;
        this.addedBy = addedBy;
        this.addedAt = addedAt;
    }
}
