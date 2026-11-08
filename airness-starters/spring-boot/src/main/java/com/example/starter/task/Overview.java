package com.example.starter.task;

/**
 * How the tasks a reader may see stand, as the home page counts them.
 *
 * @param open       how many nobody has started
 * @param inProgress how many somebody is working on
 * @param done       how many are finished
 * @param overdue    how many are past their due day and not done
 */
public record Overview(long open, long inProgress, long done, long overdue) {
}
