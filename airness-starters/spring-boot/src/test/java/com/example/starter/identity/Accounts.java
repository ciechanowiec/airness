package com.example.starter.identity;

import com.example.starter.Caller;
import java.util.Locale;
import java.util.UUID;
import lombok.experimental.UtilityClass;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The accounts the tests admit. Every test admits accounts of its own, under addresses nobody else uses,
 * because the suite shares one database and runs in a shuffled order: a test that changed an account
 * another test reads would decide the outcome of whichever test ran next.
 *
 * <p>Nothing here judges anything: every method answers a value, and the tests assert.
 */
@UtilityClass
public final class Accounts {

    /**
     * The password every account the tests admit signs in with. It reaches only a server a test started.
     */
    public static final String SECRET = "another-" + "fixture-not-a-secret";

    /**
     * Answers an address nobody has signed in with yet.
     *
     * @param who a word saying whose address it is, which keeps a failing test legible
     * @return a fresh address
     */
    public static String fresh(String who) {
        return "%s-%s@starter.test".formatted(who, UUID.randomUUID()).toLowerCase(Locale.ROOT);
    }

    /**
     * Admits an account through the application layer, as the first administrator.
     *
     * @param users the accounts of the application
     * @param who   a word saying whose account it is
     * @param role  what the account may do
     * @return the address the account signs in with
     */
    public static String admitted(Users users, String who, Role role) {
        String email = fresh(who);
        Caller.acting(Caller.ADMINISTRATOR, Role.ADMIN.getAuthority());
        try {
            users.register(new UserForm(new UserProfile(email, "Test %s".formatted(who)), role, SECRET));
        } finally {
            SecurityContextHolder.clearContext();
        }
        return email;
    }
}
