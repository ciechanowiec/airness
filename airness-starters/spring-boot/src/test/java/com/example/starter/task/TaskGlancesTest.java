package com.example.starter.task;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.starter.Caller;
import com.example.starter.DatabaseTest;
import com.example.starter.identity.Accounts;
import com.example.starter.identity.Role;
import com.example.starter.identity.Users;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

@DatabaseTest
class TaskGlancesTest {

    // Open tasks beyond the ones whose counting a test judges, so the account is among the most occupied,
    // which are the people the load shows, whatever other tests left in the database.
    private static final int SURPLUS = 12;

    private final Users users;

    private final Tasks tasks;

    private final TaskGlances glances;

    // The day by the clock the application reads, which is what decides whether a task is late.
    private final LocalDate today;

    TaskGlancesTest(Users users, Tasks tasks, TaskGlances glances, Clock clock) {
        this.users = users;
        this.tasks = tasks;
        this.glances = glances;
        this.today = LocalDate.now(clock);
    }

    @AfterEach
    void actAsNobody() {
        SecurityContextHolder.clearContext();
    }

    private void written(String email, TaskStatus status, LocalDate dueOn) {
        Notes.written(this.tasks, email, new TaskContent(Notes.title("Counted"), "", status, dueOn));
    }

    @Test
    void countsEachUnfinishedTaskOfAPersonOnceAndALateOneAsLateWhateverItsStatus() {
        String who = "glance%s".formatted(UUID.randomUUID());
        String email = Accounts.admitted(this.users, who, Role.USER);
        LocalDate yesterday = this.today.minusDays(1);
        LocalDate tomorrow = this.today.plusDays(1);
        this.written(email, TaskStatus.OPEN, yesterday);
        this.written(email, TaskStatus.IN_PROGRESS, yesterday);
        this.written(email, TaskStatus.IN_PROGRESS, tomorrow);
        this.written(email, TaskStatus.OPEN, this.today);
        this.written(email, TaskStatus.DONE, yesterday);
        IntStream.range(0, SURPLUS).forEach(_ -> this.written(email, TaskStatus.OPEN, tomorrow));
        Caller.acting(Caller.ADMINISTRATOR, Role.ADMIN.getAuthority());
        String name = "Test %s".formatted(who);
        List<Load> found = this.glances.load().stream().filter(load -> name.equals(load.name())).toList();
        assertEquals(List.of(new Load(name, 1, 1 + SURPLUS, 2)), found);
    }

    @Test
    void showsTheLoadToNobodyButAnAdministrator() {
        String email = Accounts.admitted(this.users, "unshown", Role.USER);
        this.written(email, TaskStatus.OPEN, this.today);
        Caller.acting(email, Role.USER.getAuthority());
        assertEquals(List.of(), this.glances.load());
    }
}
