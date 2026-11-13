package com.example.starter.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.starter.Caller;
import com.example.starter.DatabaseTest;
import com.example.starter.identity.Accounts;
import com.example.starter.identity.Role;
import com.example.starter.identity.Users;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The rules the application layer of the tasks holds to, whatever page asked.
 */
@DatabaseTest
class TasksTest {

    private final Users users;

    private final Tasks tasks;

    private final JdbcTemplate database;

    // The day by the clock the application reads, which is what decides whether a task is overdue.
    private final LocalDate today;

    TasksTest(Users users, Tasks tasks, Clock clock, JdbcTemplate database) {
        this.users = users;
        this.tasks = tasks;
        this.database = database;
        this.today = LocalDate.now(clock);
    }

    // The entries of the trail naming the given task, made by the account under the given address.
    private long recorded(String actor, UUID task, String action) {
        return Objects.requireNonNull(
            this.database.queryForObject(
                """
                    SELECT COUNT(*) FROM audit_event
                    JOIN app_user ON audit_event.actor = CAST(app_user.reference AS VARCHAR)
                    WHERE app_user.email = ? AND audit_event.target = ? AND audit_event.action = ?""",
                Long.class, actor, task.toString(), action
            )
        );
    }

    private UUID someoneElses() {
        return Notes.written(this.tasks, this.reader(), Notes.open(Notes.title("Overseen"), this.today));
    }

    @AfterEach
    void actAsNobody() {
        SecurityContextHolder.clearContext();
    }

    private String reader() {
        return Accounts.admitted(this.users, "rules", Role.USER);
    }

    @Test
    void refusesToChangeATaskTheReaderMayNotSee() {
        UUID task = Notes.written(this.tasks, this.reader(), Notes.open(Notes.title("Theirs"), this.today));
        Caller.acting(this.reader(), Role.USER.getAuthority());
        TaskContent content = Notes.open("Mine now", this.today);
        assertThrows(IllegalStateException.class, () -> this.tasks.revise(task, content, 0));
    }

    @Test
    void refusesAChangeMadeAgainstAVersionNoLongerCurrent() {
        String email = this.reader();
        UUID task = Notes.written(this.tasks, email, Notes.open(Notes.title("Raced"), this.today));
        Caller.acting(email, Role.USER.getAuthority());
        this.tasks.revise(task, Notes.open("First", this.today), 0);
        TaskContent second = Notes.open("Second", this.today);
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> this.tasks.revise(task, second, 0));
    }

    @Test
    void countsTheTasksTheReaderMaySeeByHowTheyStand() {
        String email = this.reader();
        Notes.written(this.tasks, email, Notes.open(Notes.title("Late"), this.today.minusDays(1)));
        Notes.written(this.tasks, email, Notes.open(Notes.title("Timely"), this.today.plusDays(1)));
        Notes.written(
            this.tasks, email, new TaskContent(Notes.title("Finished"), "", TaskStatus.DONE, this.today.minusDays(1))
        );
        Notes.written(this.tasks, this.reader(), Notes.open(Notes.title("Elsewhere"), this.today));
        Caller.acting(email, Role.USER.getAuthority());
        assertEquals(new Overview(2, 0, 1, 1), this.tasks.overview());
    }

    @Test
    void findsAPercentSignAsWrittenRatherThanAsAWildcard() {
        String email = this.reader();
        String marker = UUID.randomUUID().toString();
        Notes.written(this.tasks, email, Notes.open("%s 100%% done".formatted(marker), this.today));
        Notes.written(this.tasks, email, Notes.open("%s 100 times over".formatted(marker), this.today));
        Caller.acting(email, Role.USER.getAuthority());
        List<TaskView> found = this.tasks.listing(new TaskFilter("%s 100%%".formatted(marker), "", ""), 1).items();
        assertEquals(
            List.of("%s 100%% done".formatted(marker)), found.stream().map(task -> task.content().title()).toList()
        );
    }

    @Test
    void recordsAnAdministratorRevisingATaskSomebodyElseKeeps() {
        UUID task = this.someoneElses();
        Caller.acting(Caller.ADMINISTRATOR, Role.ADMIN.getAuthority());
        this.tasks.revise(task, Notes.open("Revised by the administrator", this.today), 0);
        assertEquals(1, this.recorded(Caller.ADMINISTRATOR, task, "TASK_REVISED"));
    }

    @Test
    void recordsAnAdministratorMovingATaskSomebodyElseKeeps() {
        UUID task = this.someoneElses();
        Caller.acting(Caller.ADMINISTRATOR, Role.ADMIN.getAuthority());
        this.tasks.move(task, TaskStatus.DONE);
        assertEquals(1, this.recorded(Caller.ADMINISTRATOR, task, "TASK_MOVED"));
    }

    @Test
    void recordsAnAdministratorRemovingATaskSomebodyElseKeeps() {
        UUID task = this.someoneElses();
        Caller.acting(Caller.ADMINISTRATOR, Role.ADMIN.getAuthority());
        this.tasks.remove(task);
        assertEquals(1, this.recorded(Caller.ADMINISTRATOR, task, "TASK_REMOVED"));
    }

    @Test
    void recordsNothingWhenAReaderMovesTheirOwnTask() {
        String email = this.reader();
        UUID task = Notes.written(this.tasks, email, Notes.open(Notes.title("Own"), this.today));
        Caller.acting(email, Role.USER.getAuthority());
        this.tasks.move(task, TaskStatus.DONE);
        assertEquals(0, this.recorded(email, task, "TASK_MOVED"));
    }

    @Test
    void refusesTheTasksToSomebodyWhoHasNotSignedIn() {
        TaskFilter filter = new TaskFilter("", "", "");
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> this.tasks.listing(filter, 1));
    }
}
