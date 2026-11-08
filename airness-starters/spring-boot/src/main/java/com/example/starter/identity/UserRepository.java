package com.example.starter.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
