package com.example.starter.task;

import com.example.starter.Caller;
import com.example.starter.identity.Role;
import java.time.LocalDate;
import java.util.UUID;
import lombok.experimental.UtilityClass;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The tasks the tests write. Each title carries a fresh suffix, because an administrator's list holds the
 * tasks of every test in the suite. Nothing here judges anything.
 */
@UtilityClass
public final class Notes {

    /**
     * Answers a title nobody else's task carries.
     *
     * @param what a word saying what the task is, which keeps a failing test legible
     * @return a fresh title
     */
    public static String title(String what) {
        return "%s %s".formatted(what, UUID.randomUUID());
    }

    /**
     * Writes a task for the given account through the application layer.
     *
     * @param tasks   the tasks of the application
     * @param email   the address of the account that keeps the task
     * @param content what the task says
     * @return the reference of the task written
     */
    public static UUID written(Tasks tasks, String email, TaskContent content) {
        Caller.acting(email, Role.USER.getAuthority());
        try {
            return tasks.create(content);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Answers an open task with the given title, due on the given day.
     *
     * @param title what is to be done
     * @param dueOn the day it is due
     * @return what the task says
     */
    public static TaskContent open(String title, LocalDate dueOn) {
        return new TaskContent(title, "", TaskStatus.OPEN, dueOn);
    }
}
