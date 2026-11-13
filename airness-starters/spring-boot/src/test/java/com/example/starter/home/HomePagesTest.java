package com.example.starter.home;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.Caller;
import com.example.starter.DatabaseTest;
import com.example.starter.identity.Accounts;
import com.example.starter.identity.Role;
import com.example.starter.identity.Users;
import com.example.starter.task.Notes;
import com.example.starter.task.Tasks;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

@DatabaseTest
class HomePagesTest {

    // A due day older than any other test writes, so the task leads the list of what needs attention.
    private static final int LONG_AGO = 3650;

    private final Users users;

    private final Tasks tasks;

    private final Clock clock;

    HomePagesTest(Users users, Tasks tasks, Clock clock) {
        this.users = users;
        this.tasks = tasks;
        this.clock = clock;
    }

    @Test
    void introducesTheApplicationToSomebodyWhoHasNotSignedIn(@LocalServerPort int port) {
        assertTrue(new Caller(port).get("/").body().contains("Keep your tasks in one place"));
    }

    @Test
    void showsASignedInReaderHowTheirTasksStand(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "home", Role.USER);
        assertTrue(Caller.signedIn(port, email, Accounts.SECRET).get("/").body().contains("/tasks?status=OPEN"));
    }

    @Test
    void showsAnAdministratorWhatNeedsAttentionTheWeekAndWhatEachPersonKeeps(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "load", Role.USER);
        String title = Notes.title("Forgotten");
        Notes.written(this.tasks, email, Notes.open(title, LocalDate.now(this.clock).minusDays(LONG_AGO)));
        String page = Caller.administrator(port).get("/").body();
        assertAll(
            () -> assertTrue(page.contains(title), page),
            () -> assertTrue(page.contains("This week")),
            () -> assertTrue(page.contains("Open tasks per person"))
        );
    }
}
