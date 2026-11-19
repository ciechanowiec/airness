package com.example.starter.task;

import com.example.starter.media.MediaView;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * What a page shows about one task.
 *
 * @param reference the reference the task is known by outside the database, as a link carries it
 * @param content   what the task says
 * @param overdue   whether the task is past its due day and not done
 * @param owner     the name of the account that keeps the task, which is shown to administrators alone
 *                  and is empty for everybody else
 * @param version   the version the task is at, which a change is made against
 * @param days      the days from today to the due day, negative for a day already past
 * @param cover     the first picture attached to the task, which a list and a board show it by, or
 *                  {@link MediaView#NONE} where it has none
 */
public record TaskView(
    String reference,
    TaskContent content,
    boolean overdue,
    String owner,
    long version,
    long days,
    MediaView cover
) {

    /**
     * Answers what a page shows about the given task on the given day.
     *
     * @param task  the task as the database holds it
     * @param today the day the page is drawn on, in the zone the application keeps
     * @param owner the name of the account that keeps it, or empty where the reader is not shown it
     * @param cover the first picture attached to it, or {@link MediaView#NONE}
     * @return that task as a page shows it
     */
    public static TaskView of(Task task, LocalDate today, String owner, MediaView cover) {
        TaskContent content = task.getContent();
        return new TaskView(
            task.getReference().toString(), content, content.overdueOn(today), owner, task.getVersion(),
            ChronoUnit.DAYS.between(today, content.dueOn()), cover
        );
    }
}
