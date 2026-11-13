package com.example.starter.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * What an account may do, and whether it may do anything at all. The two travel together because every
 * decision about an account reads both: a disabled administrator is refused what a disabled user is.
 *
 * @param role    what the account may do
 * @param enabled whether the account may sign in at all
 */
@Embeddable
public record UserAccess(
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    Role role,

    @Column(name = "enabled", nullable = false) boolean enabled
) {

    /**
     * Answers whether this is the access of the only administrator left who may sign in. An account
     * disabled already, or holding another role, is nobody's last administrator whatever the count says.
     *
     * @param enabledAdministrators how many administrators may sign in
     * @return whether this account is the last of them
     */
    public boolean soleAdministrator(long enabledAdministrators) {
        return this.enabled && this.role == Role.ADMIN && enabledAdministrators == 1;
    }
}
