package com.example.starter.task;

import java.util.List;

/**
 * One column of the board: the tasks standing in one status, the earliest due first.
 *
 * @param status the status the column holds
 * @param tasks  the tasks the column shows, at most as many as the board draws
 * @param total  every task the reader may see in that status
 */
public record BoardColumn(TaskStatus status, List<TaskView> tasks, long total) {

    /**
     * Keeps a copy of the tasks out of reach of whoever handed them over.
     *
     * @param status the status the column holds
     * @param tasks  the tasks the column shows
     * @param total  every task the reader may see in that status
     */
    public BoardColumn {
        tasks = List.copyOf(tasks);
    }

    /**
     * Answers how many tasks of the column the board leaves to the list.
     *
     * @return the tasks not drawn
     */
    public long hidden() {
        return this.total - this.tasks.size();
    }
}
