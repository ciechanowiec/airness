package com.example.starter.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserTest {

    private static User account() {
        return new User(
            new UserProfile("ada@starter.test", "Ada"), new UserAccess(Role.USER, true), "hash", Instant.now()
        );
    }

    @Test
    void staysTheSameAccountWhenChanged() {
        User account = account();
        assertEquals(account, account.enabled(false));
    }

    @Test
    void hashesAsTheSameAccountWhenChanged() {
        User account = account();
        assertEquals(account.hashCode(), account.withPassword("another").hashCode());
    }

    @Test
    void isAnotherAccountThanOneAdmittedSeparately() {
        assertNotEquals(account(), account());
    }

    @Test
    void keepsWhetherItMaySignInWhenRestated() {
        User stopped = account().enabled(false);
        assertFalse(
            stopped.describedBy(new UserProfile("ada@starter.test", "Ada"), Role.ADMIN).getAccess().enabled()
        );
    }

    @Test
    void isNoOtherKindOfObject() {
        User account = account();
        Object reference = account.getReference();
        assertNotEquals(account, reference, "an account equals only an account, not even the reference it is known by");
    }
}
