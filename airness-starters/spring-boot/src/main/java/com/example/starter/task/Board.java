package com.example.starter.task;

import java.util.List;

/**
 * The tasks the reader may see, laid out in one column for each status.
 *
 * @param columns the columns, in the order the statuses are declared
 */
public record Board(List<BoardColumn> columns) {

    /**
     * Keeps a copy of the columns out of reach of whoever handed them over.
     *
     * @param columns the columns
     */
    public Board {
        columns = List.copyOf(columns);
    }
}
