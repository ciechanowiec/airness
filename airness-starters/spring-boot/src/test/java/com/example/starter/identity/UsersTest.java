package com.example.starter.identity;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.Caller;
import com.example.starter.DatabaseTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The rules the application layer refuses on, whatever page asked. Each is asked of the application layer
 * directly, because a page that never offers a control is no proof the request behind it is refused.
 */
@DatabaseTest
class UsersTest {

    private final Users users;

    private final Roster roster;

    private final UserRepository repository;

    UsersTest(Users users, Roster roster, UserRepository repository) {
        this.users = users;
        this.roster = roster;
        this.repository = repository;
    }

    @AfterEach
    void actAsNobody() {
        SecurityContextHolder.clearContext();
    }

    private UUID administrator() {
        return this.repository.findByProfileEmail(Caller.ADMINISTRATOR).orElseThrow().getReference();
    }

    // Every account these rules are asked about is an ordinary one, because none of them turns on the role.
    private static UserForm form(String email, String password) {
        return new UserForm(new UserProfile(email, "Somebody"), Role.USER, password);
    }

    private static void actAsAdministrator() {
        Caller.acting(Caller.ADMINISTRATOR, Role.ADMIN.getAuthority());
    }

    private static void actAsUser() {
        Caller.acting(Caller.ADMINISTRATOR, Role.USER.getAuthority());
    }

    @Test
    void refusesToAdmitAnAddressTwice() {
        actAsAdministrator();
        UserForm form = form(Caller.ADMINISTRATOR, Accounts.SECRET);
        assertThrows(IllegalStateException.class, () -> this.users.register(form));
    }

    @Test
    void refusesToAdmitAnAccountWithAPasswordThePolicyRefuses() {
        actAsAdministrator();
        UserForm form = form(Accounts.fresh("weak"), "short");
        assertThrows(IllegalStateException.class, () -> this.users.register(form));
    }

    @Test
    void refusesToRestateAnAccountNobodyCarries() {
        actAsAdministrator();
        UUID nobody = UUID.randomUUID();
        UserForm form = form(Accounts.fresh("ghost"), "");
        assertThrows(IllegalStateException.class, () -> this.users.restate(nobody, form));
    }

    @Test
    void refusesToRestateAnAccountToAnAddressAnotherSignsInWith() {
        String email = Accounts.admitted(this.users, "taker", Role.USER);
        UUID reference = this.repository.findByProfileEmail(email).orElseThrow().getReference();
        actAsAdministrator();
        UserForm form = form(Caller.ADMINISTRATOR, "");
        assertThrows(IllegalStateException.class, () -> this.users.restate(reference, form));
    }

    @Test
    void refusesToMakeTheLastAdministratorAUser() {
        UUID administrator = this.administrator();
        actAsAdministrator();
        UserForm form = form(Caller.ADMINISTRATOR, "");
        assertThrows(IllegalStateException.class, () -> this.users.restate(administrator, form));
    }

    @Test
    void refusesToStopTheLastAdministrator() {
        UUID administrator = this.administrator();
        actAsAdministrator();
        assertThrows(IllegalStateException.class, () -> this.users.enabling(administrator, false));
    }

    @Test
    void writesNoSecondFirstAdministrator() {
        assertTrue(
            this.users.bootstrap(new UserProfile(Accounts.fresh("second"), "Second"), Accounts.SECRET).isEmpty()
        );
    }

    @Test
    void refusesAChangedPasswordProvedByAWrongCurrentOne() {
        String email = Accounts.admitted(this.users, "unproved", Role.USER);
        Caller.acting(email, Role.USER.getAuthority());
        assertThrows(IllegalStateException.class, () -> this.users.changePassword("wrong-password", Accounts.SECRET));
    }

    @Test
    void refusesAReaderWithoutAnAccount() {
        Caller.acting(Accounts.fresh("ghost"), Role.USER.getAuthority());
        assertThrows(IllegalStateException.class, () -> this.users.proves(Accounts.SECRET));
    }

    @Test
    void refusesToAdmitAnAccountForAUser() {
        actAsUser();
        UserForm form = form(Accounts.fresh("refused"), Accounts.SECRET);
        assertThrows(AccessDeniedException.class, () -> this.users.register(form));
    }

    @Test
    void refusesToStopAnAccountForAUser() {
        actAsUser();
        UUID reference = UUID.randomUUID();
        assertThrows(AccessDeniedException.class, () -> this.users.enabling(reference, false));
    }

    @Test
    void refusesTheAccountsToAUser() {
        actAsUser();
        assertThrows(AccessDeniedException.class, () -> this.roster.people(1));
    }

    @Test
    void refusesOneAccountToAUser() {
        actAsUser();
        UUID reference = UUID.randomUUID();
        assertThrows(AccessDeniedException.class, () -> this.roster.person(reference));
    }

    @Test
    void refusesToSayWhetherAnAddressIsTakenToAUser() {
        actAsUser();
        assertThrows(AccessDeniedException.class, () -> this.roster.taken(Caller.ADMINISTRATOR));
    }

    @Test
    void refusesTheRolesToAUser() {
        actAsUser();
        assertThrows(AccessDeniedException.class, this.roster::roles);
    }
}
