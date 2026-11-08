package com.example.starter.task;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

@DatabaseTest
class TaskListPagesTest {

    private static final int DAYS_AHEAD = 3;

    private static final String TASKS = "/tasks";

    private static final Pattern CHOSEN_DONE = Pattern.compile("value=\"DONE\"\\s+selected");

    private final Users users;

    private final Tasks tasks;

    // A day still ahead by the clock the application reads, so no task written here is overdue.
    private final LocalDate soon;

    TaskListPagesTest(Users users, Tasks tasks, Clock clock) {
        this.users = users;
        this.tasks = tasks;
        this.soon = LocalDate.now(clock).plusDays(DAYS_AHEAD);
    }

    private static String landing(HttpResponse<String> response) {
        return response.headers().firstValue(HttpHeaders.LOCATION).orElse("");
    }

    private String reader() {
        return Accounts.admitted(this.users, "tasks", Role.USER);
    }

    @Test
    void listsTheTasksOfTheReader(@LocalServerPort int port) {
        String email = this.reader();
        String title = Notes.title("Listed");
        Notes.written(this.tasks, email, Notes.open(title, this.soon));
        assertTrue(Caller.signedIn(port, email, Accounts.SECRET).get(TASKS).body().contains(title));
    }

    @Test
    void answersHtmxWithTheResultsAlone(@LocalServerPort int port) {
        String page = Caller.signedIn(port, this.reader(), Accounts.SECRET).partial(TASKS).body();
        assertAll(
            () -> assertTrue(page.contains("id=\"results\"")),
            () -> assertFalse(page.contains("<html"))
        );
    }

    @Test
    void findsTheTasksWhoseTitleHoldsWhatWasSearched(@LocalServerPort int port) {
        String email = this.reader();
        String found = Notes.title("Found");
        String other = Notes.title("Other");
        Notes.written(this.tasks, email, Notes.open(found, this.soon));
        Notes.written(this.tasks, email, Notes.open(other, this.soon));
        String page = Caller.signedIn(port, email, Accounts.SECRET)
            .get("/tasks?search=%s".formatted(Caller.encoded(found)))
            .body();
        assertAll(() -> assertTrue(page.contains(found)), () -> assertFalse(page.contains(other)));
    }

    @Test
    void narrowsTheListToAStatus(@LocalServerPort int port) {
        String email = this.reader();
        String done = Notes.title("Done");
        String open = Notes.title("Open");
        Notes.written(this.tasks, email, new TaskContent(done, "", TaskStatus.DONE, this.soon));
        Notes.written(this.tasks, email, Notes.open(open, this.soon));
        String page = Caller.signedIn(port, email, Accounts.SECRET).get("/tasks?status=DONE&sort=TITLE").body();
        assertAll(() -> assertTrue(page.contains(done)), () -> assertFalse(page.contains(open)));
    }

    @Test
    void saysNothingMatchedWhereASearchFindsNothing(@LocalServerPort int port) {
        String page = Caller.signedIn(port, this.reader(), Accounts.SECRET).get("/tasks?search=nothing-like-it").body();
        assertTrue(page.contains("No task matches what you searched for."));
    }

    @Test
    void answersHtmxMovingATaskWithTheChangedRowAlone(@LocalServerPort int port) {
        String email = this.reader();
        UUID task = Notes.written(this.tasks, email, Notes.open(Notes.title("Moved"), this.soon));
        String row = Caller.signedIn(port, email, Accounts.SECRET)
            .partialPost("/tasks/%s/status".formatted(task), "status=DONE")
            .body();
        assertAll(
            () -> assertTrue(row.strip().startsWith("<tr"), row),
            () -> assertTrue(CHOSEN_DONE.matcher(row).find(), row)
        );
    }

    @Test
    void sendsAReaderWithoutScriptBackToTheListAfterMovingATask(@LocalServerPort int port) {
        String email = this.reader();
        UUID task = Notes.written(this.tasks, email, Notes.open(Notes.title("Posted"), this.soon));
        HttpResponse<String> answer = Caller.signedIn(port, email, Accounts.SECRET)
            .post("/tasks/%s/status".formatted(task), "status=IN_PROGRESS");
        assertTrue(landing(answer).endsWith("/tasks?notice=SAVED"));
    }

    @Test
    void refusesAStatusNobodyKnows(@LocalServerPort int port) {
        String email = this.reader();
        UUID task = Notes.written(this.tasks, email, Notes.open(Notes.title("Lost"), this.soon));
        HttpResponse<String> answer = Caller.signedIn(port, email, Accounts.SECRET)
            .post("/tasks/%s/status".formatted(task), "status=LOST");
        assertEquals(HttpStatus.BAD_REQUEST.value(), answer.statusCode());
    }

    @Test
    void showsAnAdministratorEveryTaskAndWhoKeepsIt(@LocalServerPort int port) {
        String title = Notes.title("Overseen");
        Notes.written(this.tasks, this.reader(), Notes.open(title, this.soon));
        String page = Caller.administrator(port).get("/tasks?search=%s".formatted(Caller.encoded(title))).body();
        assertAll(() -> assertTrue(page.contains(title)), () -> assertTrue(page.contains("Test tasks")));
    }
}
