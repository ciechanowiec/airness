package com.example.starter.identity;

import com.example.starter.web.Named;
import org.springframework.security.core.GrantedAuthority;

/**
 * What an account of the application may do. One role per account. The enum is the one place the roles
 * are declared: every authority string, and every role a security expression names, is read against
 * these constants rather than spelled out beside them.
 */
public enum Role implements GrantedAuthority, Named {

    /**
     * Keeps tasks of their own, and sees and changes nobody else's.
     */
    USER("identity.role.user"),

    /**
     * Sees and changes every task, and manages the accounts of the application.
     */
    ADMIN("identity.role.admin");

    private static final String PREFIX = "ROLE_";

    private final String key;

    Role(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return this.key;
    }

    @Override
    public String getAuthority() {
        return PREFIX + this.name();
    }
}
