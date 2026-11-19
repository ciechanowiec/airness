package com.example.starter.task;

import com.example.starter.identity.Person;

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
     * Answers this tally under the person its account is shown as.
     *
     * @param person the name and the portrait the account is shown with
     * @return the load the pages show
     */
    Load of(Person person) {
        return new Load(person.name(), person.portrait(), this.inProgress, this.open, this.late);
    }
}
