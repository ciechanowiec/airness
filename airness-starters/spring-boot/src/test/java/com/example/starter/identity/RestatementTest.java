package com.example.starter.identity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.starter.Caller;
import com.example.starter.DatabaseTest;
import com.example.starter.audit.AuditAction;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Who may restate an account, and what the audit trail keeps of a restatement: a change of role apart
 * from a change of name, and the account under the reference that outlives its address.
 */
@DatabaseTest
class RestatementTest {

    private final Users users;

    private final UserRepository repository;

    private final JdbcTemplate database;

    RestatementTest(Users users, UserRepository repository, JdbcTemplate database) {
        this.users = users;
        this.repository = repository;
        this.database = database;
    }

    @AfterEach
    void actAsNobody() {
        SecurityContextHolder.clearContext();
    }

    private User account(String email) {
        return this.repository.findByProfileEmail(email).orElseThrow();
    }

    private long recorded(UUID target, AuditAction action) {
        return Objects.requireNonNull(
            this.database.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE target = ? AND action = ?",
                Long.class, target.toString(), action.name()
            )
        );
    }

    private static UserForm form(String email, Role role) {
        return new UserForm(new UserProfile(email, "Somebody"), role, "");
    }

    @Test
    void refusesAUserWhoTriesToMakeThemselvesAnAdministrator() {
        String email = Accounts.admitted(this.users, "ambitious", Role.USER);
        UUID reference = this.account(email).getReference();
        Caller.acting(email, Role.USER.getAuthority());
        UserForm form = form(email, Role.ADMIN);
        assertAll(
            () -> assertThrows(AccessDeniedException.class, () -> this.users.restate(reference, form)),
            () -> assertEquals(Role.USER, this.account(email).getAccess().role())
        );
    }

    @Test
    void refusesAUserWhoPostsThemselvesAnAdministrator(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "climber", Role.USER);
        String body = "profile.fullName=Climber&profile.email=%s&role=%s&password=".formatted(
            Caller.encoded(email), Role.ADMIN.name()
        );
        HttpResponse<String> answer = Caller.signedIn(port, email, Accounts.SECRET)
            .post("/users/%s".formatted(this.account(email).getReference()), body);
        assertAll(
            () -> assertEquals(HttpStatus.FORBIDDEN.value(), answer.statusCode()),
            () -> assertEquals(Role.USER, this.account(email).getAccess().role())
        );
    }

    @Test
    void recordsAChangeOfRoleApartFromTheRestatementThatMadeIt() {
        String email = Accounts.admitted(this.users, "promoted", Role.USER);
        UUID reference = this.account(email).getReference();
        Caller.acting(Caller.ADMINISTRATOR, Role.ADMIN.getAuthority());
        this.users.restate(reference, form(email, Role.ADMIN));
        try {
            assertAll(
                () -> assertEquals(1, this.recorded(reference, AuditAction.ROLE_CHANGED)),
                () -> assertEquals(1, this.recorded(reference, AuditAction.ACCOUNT_RESTATED))
            );
        } finally {
            // Another administrator who may sign in would decide the tests about the last one.
            this.users.enabling(reference, false);
        }
    }

    @Test
    void recordsNoChangeOfRoleForARestatementThatKeepsIt() {
        String email = Accounts.admitted(this.users, "renamedInPlace", Role.USER);
        UUID reference = this.account(email).getReference();
        Caller.acting(Caller.ADMINISTRATOR, Role.ADMIN.getAuthority());
        this.users.restate(reference, form(email, Role.USER));
        assertEquals(0, this.recorded(reference, AuditAction.ROLE_CHANGED));
    }

    @Test
    void recordsARestatementUnderTheReferenceThatOutlivesTheAddress() {
        String email = Accounts.admitted(this.users, "readdressed", Role.USER);
        UUID reference = this.account(email).getReference();
        Caller.acting(Caller.ADMINISTRATOR, Role.ADMIN.getAuthority());
        this.users.restate(reference, form(Accounts.fresh("moved"), Role.USER));
        assertEquals(1, this.recorded(reference, AuditAction.ACCOUNT_RESTATED));
    }
}
