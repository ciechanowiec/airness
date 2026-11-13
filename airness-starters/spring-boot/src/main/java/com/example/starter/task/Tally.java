package com.example.starter.task;

/**
 * The tasks one account keeps that are not yet done, counted by how they stand, as the database adds them
 * up before the account is named. A task past its due day counts as late whatever its status, so no task
 * is counted twice.
 *
 * @param ownerId    the identifier of the account that keeps the tasks
 * @param inProgress the tasks somebody has started that are not yet late
 * @param open       the tasks nobody has started that are not yet late
 * @param late       the tasks past their due day
 */
public record Tally(long ownerId, long inProgress, long open, long late) {

    /**
     * Answers this tally under the name its account is shown under.
     *
     * @param name the name the account is shown under
     * @return the load the pages show
     */
    Load named(String name) {
        return new Load(name, this.inProgress, this.open, this.late);
    }
}
