package com.example.starter.task;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.Caller;
import com.example.starter.DatabaseTest;
import com.example.starter.identity.Accounts;
import com.example.starter.identity.Role;
import com.example.starter.identity.Users;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

@DatabaseTest
class TaskFormPagesTest {

    private static final int DAYS_AHEAD = 3;

    private static final String TASKS = "/tasks";

    private final Users users;

    private final Tasks tasks;

    // A day still ahead by the clock the application reads, so no task written here is overdue.
    private final LocalDate soon;

    TaskFormPagesTest(Users users, Tasks tasks, Clock clock) {
        this.users = users;
        this.tasks = tasks;
        this.soon = LocalDate.now(clock).plusDays(DAYS_AHEAD);
    }

    private static String landing(HttpResponse<String> response) {
        return response.headers().firstValue(HttpHeaders.LOCATION).orElse("");
    }

    // Each form is opened on the first version of its task, which is the version a stale change is made against too.
    private static String form(String title, String status, String dueOn) {
        return "title=%s&description=&status=%s&dueOn=%s&version=0".formatted(Caller.encoded(title), status, dueOn);
    }

    private String reader() {
        return Accounts.admitted(this.users, "tasks", Role.USER);
    }

    @Test
    void writesATask(@LocalServerPort int port) {
        String email = this.reader();
        String title = Notes.title("Written");
        Caller reader = Caller.signedIn(port, email, Accounts.SECRET);
        HttpResponse<String> answer = reader.post(TASKS, form(title, "OPEN", this.soon.toString()));
        assertAll(
            () -> assertTrue(landing(answer).endsWith("/tasks?notice=CREATED")),
            () -> assertTrue(reader.get(TASKS).body().contains(title))
        );
    }

    @Test
    void opensTheFormThatWritesATask(@LocalServerPort int port) {
        String page = Caller.signedIn(port, this.reader(), Accounts.SECRET).get("/tasks/new").body();
        assertTrue(page.contains("Write the task"));
    }

    @Test
    void refusesATaskWithEveryMistakeAtOnce(@LocalServerPort int port) {
        String page = Caller.signedIn(port, this.reader(), Accounts.SECRET).post(TASKS, form("", "", "")).body();
        assertAll(
            () -> assertTrue(page.contains("Say what is to be done.")),
            () -> assertTrue(page.contains("Choose how the task stands.")),
            () -> assertTrue(page.contains("Give the day the task is due."))
        );
    }

    @Test
    void refusesADueDayThatCannotBeRead(@LocalServerPort int port) {
        String page = Caller.signedIn(port, this.reader(), Accounts.SECRET)
            .post(TASKS, form("Unreadable", "OPEN", "someday"))
            .body();
        assertTrue(page.contains("This value could not be read."));
    }

    @Test
    void opensATaskToChange(@LocalServerPort int port) {
        String email = this.reader();
        String title = Notes.title("Opened");
        UUID task = Notes.written(this.tasks, email, Notes.open(title, this.soon));
        String page = Caller.signedIn(port, email, Accounts.SECRET).get("/tasks/%s/edit".formatted(task)).body();
        assertTrue(page.contains(title));
    }

    @Test
    void changesATask(@LocalServerPort int port) {
        String email = this.reader();
        UUID task = Notes.written(this.tasks, email, Notes.open(Notes.title("Before"), this.soon));
        String after = Notes.title("After");
        Caller reader = Caller.signedIn(port, email, Accounts.SECRET);
        HttpResponse<String> answer = reader.post(
            "/tasks/%s".formatted(task), form(after, "IN_PROGRESS", this.soon.toString())
        );
        assertAll(
            () -> assertTrue(landing(answer).endsWith("/tasks?notice=SAVED")),
            () -> assertTrue(reader.get(TASKS).body().contains(after))
        );
    }

    @Test
    void refusesAChangeMadeAfterSomebodyElsesChange(@LocalServerPort int port) {
        String email = this.reader();
        UUID task = Notes.written(this.tasks, email, Notes.open(Notes.title("Contested"), this.soon));
        Caller reader = Caller.signedIn(port, email, Accounts.SECRET);
        reader.post("/tasks/%s".formatted(task), form(Notes.title("First"), "OPEN", this.soon.toString()));
        String second = form(Notes.title("Second"), "OPEN", this.soon.toString());
        String page = reader.post("/tasks/%s".formatted(task), second).body();
        assertTrue(page.contains("Somebody changed this task after you opened it."));
    }

    @Test
    void refusesAChangeWithAMistakeOnTheForm(@LocalServerPort int port) {
        String email = this.reader();
        UUID task = Notes.written(this.tasks, email, Notes.open(Notes.title("Mistaken"), this.soon));
        String page = Caller.signedIn(port, email, Accounts.SECRET)
            .post("/tasks/%s".formatted(task), form("", "OPEN", this.soon.toString()))
            .body();
        assertTrue(page.contains("Say what is to be done."));
    }

    @Test
    void deletesATask(@LocalServerPort int port) {
        String email = this.reader();
        UUID task = Notes.written(this.tasks, email, Notes.open(Notes.title("Deleted"), this.soon));
        Caller reader = Caller.signedIn(port, email, Accounts.SECRET);
        HttpResponse<String> answer = reader.post("/tasks/%s/delete".formatted(task), "");
        assertAll(
            () -> assertTrue(landing(answer).endsWith("/tasks?notice=DELETED")),
            () -> assertEquals(HttpStatus.NOT_FOUND.value(), reader.get("/tasks/%s/edit".formatted(task)).statusCode())
        );
    }

    @Test
    void answersAnotherReadersTaskAsMissing(@LocalServerPort int port) {
        UUID task = Notes.written(this.tasks, this.reader(), Notes.open(Notes.title("Private"), this.soon));
        Caller stranger = Caller.signedIn(port, this.reader(), Accounts.SECRET);
        assertAll(
            () -> assertEquals(
                HttpStatus.NOT_FOUND.value(), stranger.get("/tasks/%s/edit".formatted(task)).statusCode()
            ),
            () -> assertEquals(
                HttpStatus.NOT_FOUND.value(),
                stranger.post("/tasks/%s/status".formatted(task), "status=DONE").statusCode()
            ),
            () -> assertEquals(
                HttpStatus.NOT_FOUND.value(),
                stranger.post("/tasks/%s".formatted(task), form("Taken", "DONE", this.soon.toString())).statusCode()
            ),
            () -> assertEquals(
                HttpStatus.NOT_FOUND.value(),
                stranger.post("/tasks/%s/delete".formatted(task), "").statusCode()
            )
        );
    }
}
