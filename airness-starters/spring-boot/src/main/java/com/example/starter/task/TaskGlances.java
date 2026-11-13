package com.example.starter.task;

import com.example.starter.TransactionTimeout;
import com.example.starter.identity.Owners;
import com.example.starter.identity.Reader;
import com.example.starter.identity.Role;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The views of the tasks that read many at once and change none: what needs attention, what falls due this
 * week, what each person keeps, and the board. Each starts from what the reader may see, like every read of
 * the tasks.
 *
 * <p>Every method needs somebody signed in, which is why the guard sits on the class. The class is not
 * final because the container wraps it in a proxy, which is a subclass, and both the transaction and the
 * authorization are applied by that proxy.
 */
@Service
@PreAuthorize("isAuthenticated()")
public class TaskGlances {

    // How many tasks a glance at the home page lists, and how many cards a column of the board draws.
    private static final int GLANCE = 8;

    private static final int COLUMN = 30;

    private static final int WEEK = 7;

    // How many people the load of the home page shows, the most occupied of them.
    private static final int LOADS = 20;

    private static final Sort SOONEST = Sort.by(Sort.Direction.ASC, "content.dueOn").and(Sort.by("id"));

    private final TaskRepository repository;

    private final TaskReach reach;

    private final Owners owners;

    private final Clock clock;

    /**
     * Reads the tasks through the given repository, as far as the reader may see them.
     *
     * @param repository the tasks as the database holds them
     * @param reach      which tasks the reader may see, and how a task is shown to them
     * @param owners     the accounts, whose names the load of each is shown under
     * @param clock      the clock the day of every glance is read from
     */
    public TaskGlances(TaskRepository repository, TaskReach reach, Owners owners, Clock clock) {
        this.repository = repository;
        this.reach = reach;
        this.owners = owners;
        this.clock = clock;
    }

    /**
     * Answers the tasks the reader may see that are late or due today, the oldest first.
     *
     * @return at most a glance of them
     */
    @Transactional(readOnly = true, timeout = TransactionTimeout.SECONDS)
    public List<TaskView> attention() {
        return this.soonest(this.reach.visible().and(TaskCriteria.unfinishedDueBy(LocalDate.now(this.clock))));
    }

    /**
     * Answers the tasks the reader may see that fall due in the seven days after today, the soonest first.
     *
     * @return at most a glance of them
     */
    @Transactional(readOnly = true, timeout = TransactionTimeout.SECONDS)
    public List<TaskView> week() {
        LocalDate today = LocalDate.now(this.clock);
        return this.soonest(
            this.reach.visible().and(TaskCriteria.unfinishedDueBetween(today.plusDays(1), today.plusDays(WEEK)))
        );
    }

    /**
     * Answers what the people keeping the most tasks not yet done keep, which administrators alone are
     * shown.
     *
     * @return one load per such person, by name, or nothing for anybody else
     */
    @Transactional(readOnly = true, timeout = TransactionTimeout.SECONDS)
    public List<Load> load() {
        if (!Reader.holds(Role.ADMIN)) {
            return List.of();
        }
        LocalDate today = LocalDate.now(this.clock);
        List<Tally> tallies = this.repository.tallied(
            today, TaskStatus.IN_PROGRESS, TaskStatus.DONE, Limit.of(LOADS)
        );
        Map<Long, String> names = this.owners.names(tallies.stream().map(Tally::ownerId).toList());
        return tallies.stream()
            .map(tally -> tally.named(names.getOrDefault(tally.ownerId(), "")))
            .sorted(Comparator.comparing(Load::name))
            .toList();
    }

    /**
     * Answers the tasks the reader may see, one column for each status.
     *
     * @return the board
     */
    @Transactional(readOnly = true, timeout = TransactionTimeout.SECONDS)
    public Board board() {
        Specification<Task> visible = this.reach.visible();
        return new Board(Stream.of(TaskStatus.values()).map(status -> this.column(visible, status)).toList());
    }

    private BoardColumn column(Specification<Task> visible, TaskStatus status) {
        Page<Task> found = this.repository.findAll(
            visible.and(TaskCriteria.standing(status)), PageRequest.of(0, COLUMN, SOONEST)
        );
        return new BoardColumn(status, this.reach.views(found.getContent()), found.getTotalElements());
    }

    private List<TaskView> soonest(Specification<Task> narrowed) {
        return this.reach.views(this.repository.findAll(narrowed, PageRequest.of(0, GLANCE, SOONEST)).getContent());
    }
}
