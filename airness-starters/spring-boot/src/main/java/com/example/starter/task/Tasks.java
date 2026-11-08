package com.example.starter.task;

import com.example.starter.identity.Owners;
import com.example.starter.identity.Reader;
import com.example.starter.identity.Role;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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

    private static final int TIMEOUT_SECONDS = 10;

    private static final int PAGE_SIZE = 20;

    private static final int FIRST_PAGE = 1;

    private final TaskRepository repository;

    private final Owners owners;

    private final Clock clock;

    /**
     * Reads and writes the tasks through the given repository.
     *
     * @param repository the tasks as the database holds them
     * @param owners     the accounts as the tasks they keep read them
     * @param clock      the clock every instant and every day of the application is read from
     */
    public Tasks(TaskRepository repository, Owners owners, Clock clock) {
        this.repository = repository;
        this.owners = owners;
        this.clock = clock;
    }

    /**
     * Answers the page of the tasks the reader may see that the given filter narrows the list to.
     *
     * @param filter what the reader narrowed the list to
     * @param page   which page of the list is shown, counted from one
     * @return that page of the tasks
     */
    @Transactional(readOnly = true, timeout = TIMEOUT_SECONDS)
    public TaskListing listing(TaskFilter filter, int page) {
        Page<Task> found = this.repository.findAll(filter.narrowing(this.visible()), filter.pageable(page, PAGE_SIZE));
        Map<Long, String> names = this.names(found.stream().map(Task::getOwnerId).collect(Collectors.toSet()));
        LocalDate today = LocalDate.now(this.clock);
        return new TaskListing(
            found.stream().map(task -> TaskView.of(task, today, names.getOrDefault(task.getOwnerId(), ""))).toList(),
            found.getNumber() + FIRST_PAGE,
            Math.max(found.getTotalPages(), FIRST_PAGE)
        );
    }

    /**
     * Answers one task the reader may see.
     *
     * @param reference the reference the task is known by outside the database
     * @return that task, or nothing where the reader may not see a task under that reference
     */
    @Transactional(readOnly = true, timeout = TIMEOUT_SECONDS)
    public Optional<TaskView> task(UUID reference) {
        return this.seen(reference).map(this::viewed);
    }

    /**
     * Writes a task for the reader.
     *
     * @param content what the task says
     * @return the reference the task is known by
     */
    @Transactional(readOnly = false, timeout = TIMEOUT_SECONDS)
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
    @Transactional(readOnly = true, timeout = TIMEOUT_SECONDS)
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
    @Transactional(readOnly = false, timeout = TIMEOUT_SECONDS)
    public void revise(UUID reference, TaskContent content, long version) {
        Task task = this.found(reference);
        this.repository.save(task.revised(content, this.clock.instant(), version));
    }

    /**
     * Moves the task under the given reference to the given status.
     *
     * @param reference the reference the task is known by outside the database
     * @param status    where the task now stands
     * @return the task as it now stands
     * @throws IllegalStateException when the reader may not see a task under that reference
     */
    @Transactional(readOnly = false, timeout = TIMEOUT_SECONDS)
    public TaskView move(UUID reference, TaskStatus status) {
        Task task = this.found(reference);
        Task moved = task.revised(task.getContent().moved(status), this.clock.instant(), task.getVersion());
        return this.viewed(this.repository.saveAndFlush(moved));
    }

    /**
     * Removes the task under the given reference.
     *
     * @param reference the reference the task is known by outside the database
     * @throws IllegalStateException when the reader may not see a task under that reference
     */
    @Transactional(readOnly = false, timeout = TIMEOUT_SECONDS)
    public void remove(UUID reference) {
        this.repository.delete(this.found(reference));
    }

    /**
     * Answers how the tasks the reader may see stand.
     *
     * @return the counts of those tasks by status, and of those overdue
     */
    @Transactional(readOnly = true, timeout = TIMEOUT_SECONDS)
    public Overview overview() {
        Specification<Task> visible = this.visible();
        return new Overview(
            this.repository.count(visible.and(TaskCriteria.standing(TaskStatus.OPEN))),
            this.repository.count(visible.and(TaskCriteria.standing(TaskStatus.IN_PROGRESS))),
            this.repository.count(visible.and(TaskCriteria.standing(TaskStatus.DONE))),
            this.repository.count(visible.and(TaskCriteria.overdueOn(LocalDate.now(this.clock))))
        );
    }

    // The tasks the reader may see: every task for an administrator, and their own for anybody else.
    private Specification<Task> visible() {
        return Reader.holds(Role.ADMIN) ? Specification.unrestricted() : TaskCriteria.ownedBy(this.owners.reader());
    }

    private Optional<Task> seen(UUID reference) {
        return this.repository.findByReference(reference)
            .filter(task -> Reader.holds(Role.ADMIN) || task.getOwnerId() == this.owners.reader());
    }

    private Task found(UUID reference) {
        return this.seen(reference).orElseThrow(
            () -> new IllegalStateException("No task the reader may see carries the reference %s".formatted(reference))
        );
    }

    private TaskView viewed(Task task) {
        String owner = this.names(Set.of(task.getOwnerId())).getOrDefault(task.getOwnerId(), "");
        return TaskView.of(task, LocalDate.now(this.clock), owner);
    }

    // An administrator sees whose every task is, and anybody else sees only their own, whose name they
    // know, so the names are read for administrators alone.
    private Map<Long, String> names(Set<Long> owners) {
        return Reader.holds(Role.ADMIN) ? this.owners.names(owners) : Map.of();
    }
}
