package com.example.starter.task;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;

/**
 * The ways the tasks are narrowed. Each is written once here and combined wherever tasks are read, so
 * the list a reader sees, the counts on their home page and the task they open can never disagree about
 * what is theirs.
 */
@UtilityClass
public final class TaskCriteria {

    private static final String CONTENT = "content";

    private static final char ESCAPE = '\\';

    /**
     * Answers the tasks the given account keeps.
     *
     * @param ownerId the identifier of the account
     * @return the criterion
     */
    public static Specification<Task> ownedBy(long ownerId) {
        return (root, _, builder) -> builder.equal(root.get("ownerId"), ownerId);
    }

    /**
     * Answers the task the given reference stands for.
     *
     * @param reference the reference the task is known by outside the database
     * @return the criterion
     */
    public static Specification<Task> referenced(UUID reference) {
        return (root, _, builder) -> builder.equal(root.get("reference"), reference);
    }

    /**
     * Answers the tasks that stand at the given status.
     *
     * @param status where the tasks stand
     * @return the criterion
     */
    public static Specification<Task> standing(TaskStatus status) {
        return (root, _, builder) -> builder.equal(root.get(CONTENT).get("status"), status);
    }

    /**
     * Answers the tasks whose title holds the given text, whatever its case. The wildcards of a pattern
     * are escaped, so a reader searching for a percent sign finds the titles that hold one.
     *
     * @param text the text the title holds
     * @return the criterion
     */
    public static Specification<Task> titled(String text) {
        String escaped = text.toLowerCase(Locale.ROOT)
            .replace(String.valueOf(ESCAPE), "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
        String pattern = "%%%s%%".formatted(escaped);
        return (root, _, builder) -> builder.like(
            builder.lower(root.get(CONTENT).get("title")), pattern, ESCAPE
        );
    }

    /**
     * Answers the tasks past their due day on the given day and not done.
     *
     * @param today the day asked about, in the zone the application keeps
     * @return the criterion
     */
    public static Specification<Task> overdueOn(LocalDate today) {
        return (root, _, builder) -> builder.and(
            builder.notEqual(root.get(CONTENT).get("status"), TaskStatus.DONE),
            builder.lessThan(root.get(CONTENT).get("dueOn"), today)
        );
    }

    /**
     * Narrows to the tasks not yet done.
     *
     * @return the narrowing
     */
    public static Specification<Task> unfinished() {
        return (root, _, builder) -> builder.notEqual(root.get(CONTENT).get("status"), TaskStatus.DONE);
    }

    /**
     * Narrows to the tasks not yet done whose due day falls between two days, both included.
     *
     * @param from the first day
     * @param to   the last day
     * @return the narrowing
     */
    public static Specification<Task> unfinishedDueBetween(LocalDate from, LocalDate to) {
        return unfinished().and(
            (root, _, builder) -> builder.between(root.get(CONTENT).get("dueOn"), from, to)
        );
    }

    /**
     * Narrows to the tasks not yet done that are due on the given day or before it.
     *
     * @param day the last due day included
     * @return the narrowing
     */
    public static Specification<Task> unfinishedDueBy(LocalDate day) {
        return unfinished().and(
            (root, _, builder) -> builder.lessThanOrEqualTo(root.get(CONTENT).get("dueOn"), day)
        );
    }
}
