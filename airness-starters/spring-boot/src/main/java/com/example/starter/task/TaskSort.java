package com.example.starter.task;

import com.example.starter.web.Named;
import java.util.Arrays;
import org.springframework.data.domain.Sort;

/**
 * The orders the list of tasks can be read in. Each ends on the identifier, so two tasks that tie on
 * what the order reads keep one place between two readings of the same page.
 */
public enum TaskSort implements Named {

    /**
     * The task due soonest first.
     */
    DUE("task.sort.due", "content.dueOn", Sort.Direction.ASC),

    /**
     * The task written last first.
     */
    NEWEST("task.sort.newest", "createdAt", Sort.Direction.DESC),

    /**
     * By title, from A to Z.
     */
    TITLE("task.sort.title", "content.title", Sort.Direction.ASC);

    private static final String TIEBREAK = "id";

    private final String key;

    private final String property;

    private final Sort.Direction direction;

    TaskSort(String key, String property, Sort.Direction direction) {
        this.key = key;
        this.property = property;
        this.direction = direction;
    }

    /**
     * Answers the order a constant name stands for, which is the first one where no order carries it.
     *
     * @param name the name of the constant, as the list's address carries it
     * @return the order of that name, or the order by due date
     */
    public static TaskSort named(String name) {
        return Arrays.stream(values()).filter(sort -> sort.name().equals(name)).findFirst().orElse(DUE);
    }

    /**
     * Answers the order the repository reads the tasks in.
     *
     * @return the order, ending on the identifier
     */
    public Sort order() {
        return Sort.by(this.direction, this.property).and(Sort.by(Sort.Direction.ASC, TIEBREAK));
    }

    @Override
    public String key() {
        return this.key;
    }
}
