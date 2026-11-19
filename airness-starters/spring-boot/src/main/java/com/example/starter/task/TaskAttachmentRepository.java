package com.example.starter.task;

import com.example.starter.media.MediaKind;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * The files attached to tasks as the database holds them.
 */
public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, Long> {

    /**
     * Answers the attachments of a task in the order they were attached.
     *
     * @param taskId the identifier of the task
     * @param limit  how many attachments to answer at most
     * @return the attachments, the first attached first
     */
    List<TaskAttachment> findByTaskIdOrderByAddedAtAscIdAsc(long taskId, Limit limit);

    /**
     * Answers how many files are attached to a task.
     *
     * @param taskId the identifier of the task
     * @return how many there are
     */
    long countByTaskId(long taskId);

    /**
     * Answers the attachment that holds the given stored file.
     *
     * @param mediaId the identifier of the stored file
     * @return the attachment, or nothing where the file is attached to no task
     */
    Optional<TaskAttachment> findByMediaId(long mediaId);

    /**
     * Answers the attachment of the given task that holds the stored file known by the given reference.
     *
     * @param taskId the identifier of the task
     * @param media  the reference the stored file is known by
     * @return the attachment, or nothing where the task holds no such file
     */
    @Query(
        """
            SELECT attachment
            FROM TaskAttachment attachment, MediaObject media
            WHERE attachment.mediaId = media.id AND attachment.taskId = :task AND media.reference = :media
            """
    )
    Optional<TaskAttachment> held(@Param("task") long taskId, @Param("media") UUID media);

    /**
     * Answers the first image attached to each of the given tasks, which a list and a board show the task
     * by.
     *
     * @param tasks the identifiers of the tasks
     * @param kind  the kind of file a task is shown by
     * @param limit how many attachments to answer at most
     * @return one attachment per task that holds an image, in no particular order
     */
    @Query(
        """
            SELECT attachment
            FROM TaskAttachment attachment
            WHERE attachment.id IN (
                SELECT MIN(first.id)
                FROM TaskAttachment first, MediaObject media
                WHERE first.mediaId = media.id AND first.taskId IN :tasks AND media.file.kind = :kind
                GROUP BY first.taskId
            )
            """
    )
    List<TaskAttachment> covers(
        @Param("tasks") Collection<Long> tasks,
        @Param("kind") MediaKind kind,
        Limit limit
    );
}
