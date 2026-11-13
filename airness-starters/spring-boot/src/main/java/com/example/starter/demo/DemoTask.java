package com.example.starter.demo;

import com.example.starter.task.TaskContent;
import com.example.starter.task.TaskStatus;
import java.time.LocalDate;

/**
 * One task of the demonstration data, with its due day counted from the day the data is written, so an
 * overdue task stays overdue and an upcoming one upcoming however long after the release the data is
 * written.
 *
 * @param title       what the task is called
 * @param description what the task asks for
 * @param status      how far the task has come
 * @param dueInDays   the days from the day of writing to the due day, negative for a day already past
 */
record DemoTask(String title, String description, TaskStatus status, int dueInDays) {

    /**
     * Answers the content of the task as written on the given day.
     *
     * @param today the day the data is written
     * @return the content of the task
     */
    TaskContent content(LocalDate today) {
        return new TaskContent(this.title, this.description, this.status, today.plusDays(this.dueInDays));
    }
}
