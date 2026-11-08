package com.example.starter.task;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * The tasks as the database holds them. The list is read through the criteria of {@link TaskCriteria},
 * so one way of narrowing serves the list, the counts of the home page and the check of who may see a
 * task.
 */
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    /**
     * Answers the task the given reference stands for.
     *
     * @param reference the reference the task is known by outside the database
     * @return that task, or nothing when nobody carries the reference
     */
    Optional<Task> findByReference(UUID reference);
}
