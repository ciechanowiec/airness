package com.example.starter.identity;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * An account that signs in to the application.
 *
 * <p>The class is not final and neither are its fields, because Hibernate subclasses a persistent type
 * and writes its fields after the constructor has run. Nothing in this codebase writes one: a change of
 * any kind produces a new instance carrying the same identity, which the application layer saves.
 *
 * <p>An account is never removed. It is disabled, which stops it signing in and leaves every task it
 * kept and every audit entry naming it pointing at an account that still exists.
 *
 * <p>What is stored is the hash of a password and never the password.
 *
 * <p>Equality reads the reference rather than the generated identifier, so an account held in a set
 * before it is written is still found in that set afterwards.
 */
@Entity(name = "User")
@Access(AccessType.FIELD)
@Table(name = "app_user")
@Getter
public class User {

    private static final int HASH_LIMIT = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private long id;

    @Column(name = "reference", nullable = false, unique = true, updatable = false)
    private UUID reference;

    @Embedded
    private UserProfile profile;

    @Embedded
    private UserAccess access;

    @Embedded
    private Lockout lockout;

    @Column(name = "password_hash", nullable = false, length = HASH_LIMIT)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Leaves the account for the persistence provider to fill in from a row it has read.
     */
    protected User() {
        this.reference = UUID.randomUUID();
        this.profile = UserProfile.unnamed();
        this.access = new UserAccess(Role.USER, false);
        this.lockout = Lockout.clear();
        this.passwordHash = "";
        this.createdAt = Instant.EPOCH;
    }

    private User(User source, String passwordHash) {
        this.id = source.id;
        this.reference = source.reference;
        this.profile = source.profile;
        this.access = source.access;
        this.lockout = source.lockout;
        this.passwordHash = passwordHash;
        this.createdAt = source.createdAt;
    }

    // The one way an account is restated. The identity, the lockout and the day it was admitted are taken
    // from the account being changed, and everything else from the values given.
    private User(User source, UserProfile profile, UserAccess access) {
        this.id = source.id;
        this.reference = source.reference;
        this.profile = profile;
        this.access = access;
        this.lockout = source.lockout;
        this.passwordHash = source.passwordHash;
        this.createdAt = source.createdAt;
    }

    /**
     * Admits an account to the application.
     *
     * @param profile      how the account is named and reached
     * @param access       what the account may do, and whether it may sign in
     * @param passwordHash the hash of the password the account signs in with
     * @param createdAt    when the account was admitted
     */
    public User(UserProfile profile, UserAccess access, String passwordHash, Instant createdAt) {
        this.reference = UUID.randomUUID();
        this.profile = profile;
        this.access = access;
        this.lockout = Lockout.clear();
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    /**
     * Answers this account as somebody has now restated it, keeping whether it may sign in.
     *
     * @param profile how the account is named and reached
     * @param role    what the account may do
     * @return the restated account, which the application layer saves over this one
     */
    public User describedBy(UserProfile profile, Role role) {
        return new User(this, profile, new UserAccess(role, this.access.enabled()));
    }

    /**
     * Answers this account allowed to sign in or stopped from signing in.
     *
     * @param allowed whether the account may sign in at all
     * @return the account under that standing
     */
    public User enabled(boolean allowed) {
        return new User(this, this.profile, new UserAccess(this.access.role(), allowed));
    }

    /**
     * Answers this account signing in with the password the given hash was made from.
     *
     * @param hash the hash of the new password
     * @return the account with its new password
     */
    public User withPassword(String hash) {
        return new User(this, hash);
    }

    /**
     * Answers whether the other object is the same account, which is decided by the reference.
     *
     * @param other the object to compare this account with
     * @return whether the two are the same account
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof User user && this.reference.equals(user.reference);
    }

    /**
     * Answers the hash that follows the reference this account is known by.
     *
     * @return the hash of this account
     */
    @Override
    public int hashCode() {
        return this.reference.hashCode();
    }
}
