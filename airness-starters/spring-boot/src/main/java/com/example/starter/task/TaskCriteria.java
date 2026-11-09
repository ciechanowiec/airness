package com.example.starter.task;

import java.time.LocalDate;
import java.util.Locale;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;

/**
 * The ways the tasks are narrowed. Each is written once here and combined wherever tasks are read, so
 * the list a reader sees and the counts on their home page can never disagree about what is theirs.
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
}
