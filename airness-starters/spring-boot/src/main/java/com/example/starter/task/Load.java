package com.example.starter.task;

/**
 * The tasks one person keeps that are not yet done, counted by how they stand, as {@link Tally} counts
 * them.
 *
 * @param name       the name the person is shown under
 * @param inProgress the tasks somebody has started that are not yet late
 * @param open       the tasks nobody has started that are not yet late
 * @param late       the tasks past their due day
 */
public record Load(String name, long inProgress, long open, long late) {

    /**
     * Answers every task the person keeps that is not yet done.
     *
     * @return the sum of the three counts
     */
    public long total() {
        return this.inProgress + this.open + this.late;
    }
}
