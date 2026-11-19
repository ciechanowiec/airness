package com.example.starter.media;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The stored files as the database holds them.
 */
public interface MediaObjectRepository extends JpaRepository<MediaObject, Long> {

    /**
     * Answers the file known by the given reference.
     *
     * @param reference the reference the file is known by outside the database
     * @return that file, or nothing where none is known by it
     */
    Optional<MediaObject> findByReference(UUID reference);

    /**
     * Answers the files with the given identifiers.
     *
     * @param ids   the identifiers
     * @param limit how many files to answer at most
     * @return those files, in no particular order
     */
    List<MediaObject> findByIdIn(Collection<Long> ids, Limit limit);

    /**
     * Answers whether any file still holds the given bytes for the given purpose, which decides whether the
     * renditions of those bytes are still needed.
     *
     * @param sha256  the SHA-256 of the bytes
     * @param purpose what the renditions are drawn for
     * @return whether any file holds them
     */
    boolean existsByFileSha256AndPurpose(String sha256, MediaPurpose purpose);
}
