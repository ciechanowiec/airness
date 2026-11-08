package com.example.starter.task;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

/**
 * Something one account has to do.
 *
 * <p>A task names the account that keeps it by that account's identifier rather than holding the
 * account itself, so reading a task never loads an account, and the list reads the names it shows in
 * one query for the whole page.
 *
 * <p>The class is not final and neither are its fields, because Hibernate subclasses a persistent type
 * and writes its fields after the constructor has run. Nothing in this codebase writes one: a change
 * produces a new instance carrying the same identity, which the application layer saves.
 *
 * <p>The version is what stops one reader saving over a change another reader made after the first one
 * opened the task: a change carries the version it was made against, and the provider refuses to save
 * one made against a version that is no longer current.
 */
@Entity(name = "Task")
@Access(AccessType.FIELD)
@Table(name = "task")
@Getter
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private long id;

    @Column(name = "reference", nullable = false, unique = true, updatable = false)
    private UUID reference;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private long ownerId;

    @Embedded
    private TaskContent content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * Leaves the task for the persistence provider to fill in from a row it has read.
     */
    protected Task() {
        this.reference = UUID.randomUUID();
        this.content = new TaskContent("", "", TaskStatus.OPEN, LocalDate.EPOCH);
        this.createdAt = Instant.EPOCH;
        this.updatedAt = Instant.EPOCH;
    }

    /**
     * Writes a task for the given account.
     *
     * @param ownerId   the identifier of the account that keeps the task
     * @param content   what the task says
     * @param createdAt when the task was written
     */
    public Task(long ownerId, TaskContent content, Instant createdAt) {
        this.reference = UUID.randomUUID();
        this.ownerId = ownerId;
        this.content = content;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    // The one way a task changes. The identity, the owner and the day it was written are taken from the
    // task being changed, and what it says, when it changed and the version the change was made against are
    // taken from the values given.
    private Task(Task source, TaskContent content, Instant updatedAt, long version) {
        this.id = source.id;
        this.reference = source.reference;
        this.ownerId = source.ownerId;
        this.content = content;
        this.createdAt = source.createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    /**
     * Answers this task as it now reads, changed against the given version.
     *
     * @param revised   what the task now says
     * @param changedAt when it changed
     * @param against   the version of the task the change was made against
     * @return the changed task, which the application layer saves over this one
     */
    public Task revised(TaskContent revised, Instant changedAt, long against) {
        return new Task(this, revised, changedAt, against);
    }

    /**
     * Answers whether the other object is the same task, which is decided by the reference.
     *
     * @param other the object to compare this task with
     * @return whether the two are the same task
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof Task task && this.reference.equals(task.reference);
    }

    /**
     * Answers the hash that follows the reference this task is known by.
     *
     * @return the hash of this task
     */
    @Override
    public int hashCode() {
        return this.reference.hashCode();
    }
}
