package com.example.starter.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.starter.Caller;
import com.example.starter.DatabaseTest;
import com.example.starter.audit.AuditAction;
import com.example.starter.audit.AuditOutcome;
import com.example.starter.audit.Entries;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Every change the application layer makes to an account, as the audit trail records it.
 */
@DatabaseTest
class AccountChangesTest {

    private final Users users;

    private final UserRepository repository;

    private final Entries entries;

    AccountChangesTest(Users users, UserRepository repository, JdbcTemplate database) {
        this.users = users;
        this.repository = repository;
        this.entries = new Entries(database);
    }

    @AfterEach
    void actAsNobody() {
        SecurityContextHolder.clearContext();
    }

    private UUID reference(String email) {
        return this.repository.findByProfileEmail(email).orElseThrow().getReference();
    }

    private static void actAsAdministrator() {
        Caller.acting(Caller.ADMINISTRATOR, Role.ADMIN.getAuthority());
    }

    @Test
    void recordsAnAdmittedAccount() {
        String email = Accounts.admitted(this.users, "recorded", Role.USER);
        assertEquals(1, this.entries.written(AuditAction.ACCOUNT_CREATED, email, AuditOutcome.SUCCEEDED));
    }

    @Test
    void recordsARestatedAccountUnderTheAddressItNowSignsInWith() {
        String email = Accounts.admitted(this.users, "before", Role.USER);
        String moved = Accounts.fresh("after");
        UUID reference = this.reference(email);
        actAsAdministrator();
        this.users.restate(reference, new UserForm(new UserProfile(moved, "Moved"), Role.USER, ""));
        assertEquals(1, this.entries.written(AuditAction.ACCOUNT_RESTATED, moved, AuditOutcome.SUCCEEDED));
    }

    @Test
    void recordsAStoppedAccount() {
        String email = Accounts.admitted(this.users, "halted", Role.USER);
        UUID reference = this.reference(email);
        actAsAdministrator();
        this.users.enabling(reference, false);
        assertEquals(1, this.entries.written(AuditAction.ACCOUNT_DISABLED, email, AuditOutcome.SUCCEEDED));
    }

    @Test
    void recordsAnAccountLetSignInAgain() {
        String email = Accounts.admitted(this.users, "resumed", Role.USER);
        UUID reference = this.reference(email);
        actAsAdministrator();
        this.users.enabling(reference, false);
        this.users.enabling(reference, true);
        assertEquals(1, this.entries.written(AuditAction.ACCOUNT_ENABLED, email, AuditOutcome.SUCCEEDED));
    }

    @Test
    void recordsTheFirstAdministratorWrittenAtStartup() {
        assertEquals(
            1, this.entries.written(
                AuditAction.ADMINISTRATOR_BOOTSTRAPPED, Caller.ADMINISTRATOR, AuditOutcome.SUCCEEDED
            )
        );
    }
}
