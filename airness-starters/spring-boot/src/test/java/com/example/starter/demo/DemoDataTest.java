package com.example.starter.demo;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.Caller;
import com.example.starter.DatabaseTest;
import com.example.starter.identity.Accounts;
import com.example.starter.identity.Role;
import com.example.starter.identity.UserForm;
import com.example.starter.identity.UserProfile;
import com.example.starter.identity.Users;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

@DatabaseTest
class DemoDataTest {

    private static final String TEAMS = "%.demo.test";

    // The people of a team, without the administrator each test admits it under.
    private static final String PEOPLE
        = "SELECT COUNT(*) FROM app_user WHERE email LIKE ? AND email NOT LIKE 'administrator@%'";

    private static final String TASKS = """
        SELECT COUNT(*) FROM task JOIN app_user ON app_user.id = task.owner_id WHERE app_user.email LIKE ?""";

    private final DemoLayer layer;

    private final Users users;

    private final JdbcTemplate database;

    DemoDataTest(DemoLayer layer, Users users, JdbcTemplate database) {
        this.layer = layer;
        this.users = users;
        this.database = database;
    }

    // Every test writes under its own subdomain of demo.test, and the people and tasks are removed again,
    // so the tests that count administrators find the database as the rest of the suite left it.
    @AfterEach
    void removeTheTeams() {
        this.database.update(
            "DELETE FROM task WHERE owner_id IN (SELECT id FROM app_user WHERE email LIKE ?)", TEAMS
        );
        this.database.update("DELETE FROM app_user WHERE email LIKE ?", TEAMS);
    }

    // The administrator of a team is admitted before the team is, as the first administrator of an
    // application is written before its demonstration data, so whoever admits the team has an account.
    private void written(String domain, boolean wanted) {
        String administrator = "administrator@" + domain;
        if (this.absent(administrator)) {
            this.admitted(new UserProfile(administrator, "Administrator"), Role.ADMIN);
        }
        new DemoData(this.layer, wanted, administrator, Accounts.SECRET).run(new DefaultApplicationArguments());
    }

    private boolean absent(String email) {
        return Objects.requireNonNull(
            this.database.queryForObject("SELECT COUNT(*) FROM app_user WHERE email = ?", Long.class, email)
        ) == 0;
    }

    private void admitted(UserProfile profile, Role role) {
        Caller.acting(Caller.ADMINISTRATOR, Role.ADMIN.getAuthority());
        try {
            this.users.register(new UserForm(profile, role, Accounts.SECRET));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private long count(String query, String domain) {
        return Objects.requireNonNull(this.database.queryForObject(query, Long.class, "%@" + domain));
    }

    @Test
    void writesTheTeamAndTheirTasksOnceWhateverTheRestarts() {
        this.written("team.demo.test", true);
        this.written("team.demo.test", true);
        DemoPerson engineer = DemoCast.PEOPLE.stream()
            .filter(person -> "tomasz.nowak".equals(person.mailbox()))
            .findFirst()
            .orElseThrow();
        long tasks = DemoCast.PEOPLE.stream().mapToLong(person -> person.tasks().size()).sum();
        assertAll(
            () -> assertEquals(DemoCast.PEOPLE.size(), this.count(PEOPLE, "team.demo.test")),
            () -> assertEquals(tasks, this.count(TASKS, "team.demo.test")),
            () -> assertEquals(
                engineer.tasks().size(),
                this.database.queryForObject(TASKS, Long.class, engineer.email("team.demo.test"))
            )
        );
    }

    @Test
    void stopsTheAccountOfThePersonWhoLeft() {
        this.written("left.demo.test", true);
        assertFalse(
            Objects.requireNonNull(
                this.database.queryForObject(
                    "SELECT enabled FROM app_user WHERE email = ?", Boolean.class, "jan.zielinski@left.demo.test"
                )
            )
        );
    }

    @Test
    void countsEachDueDayFromTheDayOfWriting() {
        this.written("dated.demo.test", true);
        LocalDate due = this.database.queryForObject(
            """
                SELECT task.due_on FROM task JOIN app_user ON app_user.id = task.owner_id
                WHERE app_user.email = ? AND task.title = ?""",
            LocalDate.class, "maja.kowalska@dated.demo.test", "Approve the holiday rota"
        );
        assertEquals(this.layer.today().minusDays(2), due);
    }

    @Test
    void letsEachPersonSignInWithTheAdministratorsPassword(@LocalServerPort int port) {
        this.written("signed.demo.test", true);
        HttpResponse<String> answer = new Caller(port).signIn("emily.carter@signed.demo.test", Accounts.SECRET);
        assertTrue(answer.headers().firstValue(HttpHeaders.LOCATION).orElse("").endsWith("/"));
    }

    @Test
    void writesNothingWhereAnyOfItsPeopleAlreadyExists() {
        DemoPerson second = DemoCast.PEOPLE.get(1);
        this.admitted(new UserProfile(second.email("renamed.demo.test"), second.fullName()), second.role());
        this.written("renamed.demo.test", true);
        assertEquals(1L, this.count(PEOPLE, "renamed.demo.test"));
    }

    @Test
    void writesNothingUnlessItIsAskedFor() {
        this.written("quiet.demo.test", false);
        assertEquals(0L, this.count(PEOPLE, "quiet.demo.test"));
    }
}
