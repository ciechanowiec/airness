package com.example.starter.task;

import com.example.starter.web.Named;
import java.util.Arrays;
import java.util.Optional;

/**
 * Where a task stands.
 */
public enum TaskStatus implements Named {

    /**
     * Nobody has started it.
     */
    OPEN("task.status.open"),

    /**
     * Somebody is working on it.
     */
    IN_PROGRESS("task.status.inProgress"),

    /**
     * It is finished, so it is never overdue.
     */
    DONE("task.status.done");

    private final String key;

    TaskStatus(String key) {
        this.key = key;
    }

    /**
     * Answers the status a constant name stands for, which is how a submitted choice is read back.
     *
     * @param name the name of the constant, as a form submits it
     * @return the status of that name, and nothing where no status carries it
     */
    public static Optional<TaskStatus> named(String name) {
        return Arrays.stream(values()).filter(status -> status.name().equals(name)).findFirst();
    }

    @Override
    public String key() {
        return this.key;
    }
}
