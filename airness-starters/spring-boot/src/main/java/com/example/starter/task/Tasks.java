package com.example.starter.task;

import com.example.starter.TransactionTimeout;
import com.example.starter.audit.AuditAction;
import com.example.starter.identity.Owners;
import com.example.starter.web.Listing;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The tasks, and what may be done to one. A reader sees and changes the tasks they keep and nobody
 * else's, and an administrator sees and changes all of them. That rule is the criterion every read here
 * starts from, so a task somebody may not see is answered as one that does not exist, whether it was
 * listed, opened, changed or removed.
 *
 * <p>Every method needs somebody signed in, which is why the guard sits on the class. The class is not
 * final because the container wraps it in a proxy, which is a subclass, and both the
 * transaction and the authorization are applied by that proxy.
 */
@Service
@PreAuthorize("isAuthenticated()")
public class Tasks {

    private static final int PAGE_SIZE = 20;

    private final TaskRepository repository;

    private final Owners owners;

    private final TaskReach reach;

    private final Clock clock;

    /**
     * Reads and writes the tasks through the given repository.
     *
     * @param repository the tasks as the database holds them
     * @param owners     the accounts as the tasks they keep read them
     * @param reach      which tasks the reader may see, and how a task is shown to them
     * @param clock      the clock every instant and every day of the application is read from
     */
    public Tasks(TaskRepository repository, Owners owners, TaskReach reach, Clock clock) {
        this.repository = repository;
        this.owners = owners;
        this.reach = reach;
        this.clock = clock;
    }

    /**
     * Answers the page of the tasks the reader may see that the given filter narrows the list to.
     *
     * @param filter what the reader narrowed the list to
     * @param page   which page of the list is shown, counted from one
     * @return that page of the tasks
     */
    @Transactional(readOnly = true, timeout = TransactionTimeout.SECONDS)
    public Listing<TaskView> listing(TaskFilter filter, int page) {
        Page<Task> found = this.repository.findAll(
            filter.narrowing(this.reach.visible()), filter.pageable(page, PAGE_SIZE)
        );
        return Listing.of(found, this.reach.views(found.getContent()));
    }

    /**
     * Answers one task the reader may see.
     *
     * @param reference the reference the task is known by outside the database
     * @return that task, or nothing where the reader may not see a task under that reference
     */
    @Transactional(readOnly = true, timeout = TransactionTimeout.SECONDS)
    public Optional<TaskView> task(UUID reference) {
        return this.seen(reference).map(this.reach::view);
    }

    /**
     * Writes a task for the reader.
     *
     * @param content what the task says
     * @return the reference the task is known by
     */
    @Transactional(readOnly = false, timeout = TransactionTimeout.SECONDS)
    public UUID create(TaskContent content) {
        return this.repository.save(new Task(this.owners.reader(), content, this.clock.instant())).getReference();
    }

    /**
     * Answers whether the task under the given reference is still at the given version, which the form
     * that changes it asks before it saves anything, so a reader is told somebody changed it meanwhile.
     *
     * @param reference the reference the task is known by outside the database
     * @param version   the version the reader's form was opened on
     * @return whether nobody changed the task since
     */
    @Transactional(readOnly = true, timeout = TransactionTimeout.SECONDS)
    public boolean current(UUID reference, long version) {
        return this.seen(reference).filter(task -> task.getVersion() == version).isPresent();
    }

    /**
     * Changes what the task under the given reference says, against the version the change was made on.
     *
     * @param reference the reference the task is known by outside the database
     * @param content   what the task now says
     * @param version   the version of the task the change was made against
     * @throws IllegalStateException                                           when the reader may not see a task under
     *                                                                         that reference
     * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when somebody changed the
     *                                                                         task since that version
     */
    @Transactional(readOnly = false, timeout = TransactionTimeout.SECONDS)
    public void revise(UUID reference, TaskContent content, long version) {
        Task task = this.found(reference);
        this.repository.save(task.revised(content, this.clock.instant(), version));
        this.reach.recorded(task, AuditAction.TASK_REVISED);
    }

    /**
     * Moves the task under the given reference to the given status.
     *
     * @param reference the reference the task is known by outside the database
     * @param status    where the task now stands
     * @return the task as it now stands
     * @throws IllegalStateException when the reader may not see a task under that reference
     */
    @Transactional(readOnly = false, timeout = TransactionTimeout.SECONDS)
    public TaskView move(UUID reference, TaskStatus status) {
        Task task = this.found(reference);
        Task moved = task.revised(task.getContent().moved(status), this.clock.instant(), task.getVersion());
        TaskView view = this.reach.view(this.repository.saveAndFlush(moved));
        this.reach.recorded(task, AuditAction.TASK_MOVED);
        return view;
    }

    /**
     * Removes the task under the given reference.
     *
     * @param reference the reference the task is known by outside the database
     * @throws IllegalStateException when the reader may not see a task under that reference
     */
    @Transactional(readOnly = false, timeout = TransactionTimeout.SECONDS)
    public void remove(UUID reference) {
        Task task = this.found(reference);
        this.repository.delete(task);
        this.reach.recorded(task, AuditAction.TASK_REMOVED);
    }

    /**
     * Answers how the tasks the reader may see stand.
     *
     * @return the counts of those tasks by status, and of those overdue
     */
    @Transactional(readOnly = true, timeout = TransactionTimeout.SECONDS)
    public Overview overview() {
        Specification<Task> visible = this.reach.visible();
        return new Overview(
            this.repository.count(visible.and(TaskCriteria.standing(TaskStatus.OPEN))),
            this.repository.count(visible.and(TaskCriteria.standing(TaskStatus.IN_PROGRESS))),
            this.repository.count(visible.and(TaskCriteria.standing(TaskStatus.DONE))),
            this.repository.count(visible.and(TaskCriteria.overdueOn(LocalDate.now(this.clock))))
        );
    }

    private Optional<Task> seen(UUID reference) {
        return this.repository.findOne(this.reach.visible().and(TaskCriteria.referenced(reference)));
    }

    private Task found(UUID reference) {
        return this.seen(reference).orElseThrow(
            () -> new IllegalStateException("No task the reader may see carries the reference %s".formatted(reference))
        );
    }
}
