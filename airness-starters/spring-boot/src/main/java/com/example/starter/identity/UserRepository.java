package com.example.starter.identity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * The accounts as the database holds them.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Answers the account that signs in under the given address.
     *
     * @param email the address in its canonical spelling
     * @return the account under that address, or nothing when nobody signs in with it
     */
    Optional<User> findByProfileEmail(String email);

    /**
     * Answers the account the given reference stands for.
     *
     * @param reference the reference the account is known by outside the database
     * @return the account under that reference, or nothing when nobody carries it
     */
    Optional<User> findByReference(UUID reference);

    /**
     * Answers whether an account already signs in under the given address.
     *
     * @param email the address in its canonical spelling
     * @return whether an account under that address exists
     */
    boolean existsByProfileEmail(String email);

    /**
     * Answers whether an account other than the one under the given reference signs in under the given
     * address.
     *
     * @param email     the address in its canonical spelling
     * @param reference the reference of the account that is allowed to carry it
     * @return whether another account under that address exists
     */
    boolean existsByProfileEmailAndReferenceNot(String email, UUID reference);

    /**
     * Answers one page of every account, by the name each is shown under. Disabled accounts are among
     * them, because a list that hid them would be a list nobody could enable an account again from.
     *
     * @param page which accounts to read, and how many
     * @return that page of the accounts
     */
    @Query(
        """
            SELECT account FROM User account
            ORDER BY account.profile.fullName ASC, account.id ASC
            """
    )
    Page<User> admitted(Pageable page);

    /**
     * Answers how many enabled accounts hold the given role, which is what the rule that the application
     * always keeps an administrator who may sign in is stated against.
     *
     * @param role the role to count the holders of
     * @return how many enabled accounts hold that role
     */
    @Query(
        """
            SELECT COUNT(account) FROM User account
            WHERE account.access.role = :role AND account.access.enabled = TRUE
            """
    )
    long enabledUnder(@Param("role") Role role);

    /**
     * Takes one of the attempts the policy on refused sign-ins leaves the account under the given address
     * before it is locked. The count is raised and checked in one statement, so attempts made at once
     * each take an attempt of their own, and no more of them are let through than the policy allows.
     *
     * @param email the address in its canonical spelling
     * @param limit how many attempts in a row the policy allows
     * @param now   the instant of the attempt, before which a lock still holds
     * @return one where an attempt was taken, and zero where the account is locked, has none left, or
     *         nobody signs in under that address
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
            UPDATE User account
            SET account.lockout.failedAttempts = account.lockout.failedAttempts + 1
            WHERE account.profile.email = :email
            AND account.lockout.failedAttempts < :limit
            AND account.lockout.lockedUntil <= :now
            """
    )
    int reserved(@Param("email") String email, @Param("limit") int limit, @Param("now") Instant now);

    /**
     * Locks the account under the given address where every attempt the policy allows was taken, and
     * starts its count again.
     *
     * @param email the address in its canonical spelling
     * @param limit how many attempts in a row the policy allows
     * @param until the instant the lock lifts
     * @return one where the account was locked, and zero where it still had attempts left
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
            UPDATE User account
            SET account.lockout.failedAttempts = 0, account.lockout.lockedUntil = :until
            WHERE account.profile.email = :email AND account.lockout.failedAttempts >= :limit
            """
    )
    int locked(@Param("email") String email, @Param("limit") int limit, @Param("until") Instant until);

    /**
     * Starts the count of attempts of the account under the given address again.
     *
     * @param email the address in its canonical spelling
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
            UPDATE User account
            SET account.lockout.failedAttempts = 0
            WHERE account.profile.email = :email AND account.lockout.failedAttempts > 0
            """
    )
    void cleared(@Param("email") String email);
}
