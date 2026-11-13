package com.example.starter.task;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * The tasks as the database holds them. The list is read through the criteria of {@link TaskCriteria},
 * so one way of narrowing serves the list, the counts of the home page and the check of who may see a
 * task.
 */
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    /**
     * Answers what the accounts that keep the most tasks not yet done keep, counted by the database, so no
     * task is read to count it. Accounts keeping as many are taken in the order they were admitted.
     *
     * @param today   the day the counts are made on, before which a task is late
     * @param started the status of a task somebody has started
     * @param done    the status of a task that is done, which is counted nowhere
     * @param limit   how many accounts to answer at most
     * @return one tally per account with a task not yet done, the most occupied first
     */
    @Query(
        """
            SELECT new com.example.starter.task.Tally(
                task.ownerId,
                SUM(CASE WHEN task.content.dueOn >= :today AND task.content.status = :started THEN 1 ELSE 0 END),
                SUM(CASE WHEN task.content.dueOn >= :today AND task.content.status <> :started THEN 1 ELSE 0 END),
                SUM(CASE WHEN task.content.dueOn < :today THEN 1 ELSE 0 END)
            )
            FROM Task task
            WHERE task.content.status <> :done
            GROUP BY task.ownerId
            ORDER BY COUNT(task) DESC, task.ownerId ASC
            """
    )
    List<Tally> tallied(
        @Param("today") LocalDate today,
        @Param("started") TaskStatus started,
        @Param("done") TaskStatus done,
        Limit limit
    );
}
