package com.example.starter.media;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * One file somebody stored. The bytes live in the object store under the reference, which is chosen when
 * the file is written, so the bytes are stored before the row naming them is saved.
 *
 * <p>The class is not final and neither are its fields, because Hibernate subclasses a persistent type and
 * writes its fields after the constructor has run. Nothing in this codebase writes one.
 */
@Entity(name = "MediaObject")
@Access(AccessType.FIELD)
@Table(name = "media_object")
@Getter
public class MediaObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private long id;

    @Column(name = "reference", nullable = false, unique = true, updatable = false)
    private UUID reference;

    @Embedded
    private MediaFile file;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, updatable = false)
    private MediaPurpose purpose;

    @Column(name = "created_by", nullable = false, updatable = false)
    private long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Leaves the file for the persistence provider to fill in from a row it has read.
     */
    protected MediaObject() {
        this.reference = UUID.randomUUID();
        this.file = new MediaFile("", MediaKind.IMAGE, "", 0, "");
        this.purpose = MediaPurpose.ATTACHMENT;
        this.createdAt = Instant.EPOCH;
    }

    /**
     * Names a file somebody stored, under a reference of its own.
     *
     * @param file      what the file is
     * @param purpose   what it is for
     * @param createdBy the identifier of the account that stored it
     * @param createdAt when it was stored
     */
    public MediaObject(MediaFile file, MediaPurpose purpose, long createdBy, Instant createdAt) {
        this.reference = UUID.randomUUID();
        this.file = file;
        this.purpose = purpose;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    /**
     * Answers whether the other object is the same file, which is decided by the reference.
     *
     * @param other the object to compare this file with
     * @return whether the two are the same file
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof MediaObject media && this.reference.equals(media.reference);
    }

    /**
     * Answers the hash that follows the reference this file is known by.
     *
     * @return the hash of this file
     */
    @Override
    public int hashCode() {
        return this.reference.hashCode();
    }
}
