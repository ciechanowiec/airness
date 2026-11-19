package com.example.starter.identity;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

/**
 * The picture an account is shown with, naming the account and the stored file by their identifiers.
 *
 * <p>The class is not final and neither are its fields, because Hibernate subclasses a persistent type and
 * writes its fields after the constructor has run. Nothing in this codebase writes one.
 */
@Entity(name = "UserPortrait")
@Access(AccessType.FIELD)
@Table(name = "user_portrait")
@Getter
public class UserPortrait {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private long userId;

    @Column(name = "media_id", nullable = false, unique = true, updatable = false)
    private long mediaId;

    @Column(name = "set_at", nullable = false, updatable = false)
    private Instant setAt;

    /**
     * Leaves the portrait for the persistence provider to fill in from a row it has read.
     */
    protected UserPortrait() {
        this.setAt = Instant.EPOCH;
    }

    /**
     * Gives an account a portrait.
     *
     * @param userId  the identifier of the account
     * @param mediaId the identifier of the stored picture
     * @param setAt   when the account was given it
     */
    public UserPortrait(long userId, long mediaId, Instant setAt) {
        this.userId = userId;
        this.mediaId = mediaId;
        this.setAt = setAt;
    }
}
