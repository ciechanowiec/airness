package com.example.starter.task;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.LocalDate;

/**
 * What a task says: what is to be done, how it stands and by when. The widths are the widths the
 * migration gave the columns, and the form that states a task is held to them before anything is saved.
 *
 * @param title       what is to be done, in a line
 * @param description anything more about it, which may be empty
 * @param status      where the task stands
 * @param dueOn       the day the task is due
 */
@Embeddable
public record TaskContent(
    @Column(name = "title", nullable = false, length = TaskLimits.TITLE) String title,

    @Column(name = "description", nullable = false, length = TaskLimits.DESCRIPTION) String description,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    TaskStatus status,

    @Column(name = "due_on", nullable = false) LocalDate dueOn
) {

    /**
     * Answers whether the task is past its due day on the given day and not done.
     *
     * @param today the day asked about, in the zone the application keeps
     * @return whether the task is overdue then
     */
    public boolean overdueOn(LocalDate today) {
        return this.status != TaskStatus.DONE && this.dueOn.isBefore(today);
    }

    /**
     * Answers what this task says once it is moved to the given status.
     *
     * @param moved where the task now stands
     * @return the same task under that status
     */
    public TaskContent moved(TaskStatus moved) {
        return new TaskContent(this.title, this.description, moved, this.dueOn);
    }
}
