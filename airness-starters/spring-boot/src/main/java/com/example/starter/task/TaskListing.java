package com.example.starter.task;

import java.util.List;

/**
 * One page of the tasks, as the page that shows them needs it. The numbers are counted from one.
 *
 * @param tasks the tasks on this page
 * @param page  which page of the list this is
 * @param pages how many pages the list has, which is at least one
 */
public record TaskListing(List<TaskView> tasks, int page, int pages) {

    /**
     * Copies the tasks out of reach of whoever handed them over.
     */
    public TaskListing {
        tasks = List.copyOf(tasks);
    }
}
