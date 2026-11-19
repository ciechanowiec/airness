package com.example.starter.task;

import com.example.starter.media.MediaLibrary;
import com.example.starter.media.MediaObject;
import com.example.starter.media.MediaPurpose;
import com.example.starter.media.MediaRefusedException;
import com.example.starter.media.MediaView;
import com.example.starter.media.Upload;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * The files attached to tasks, and what may be done to them. A reader attaches files to, and takes them from,
 * the tasks they may see and no other, which is the same rule every other change to a task follows.
 *
 * <p>Attaching a file stores and renders it outside any transaction, between two short ones: the first asks
 * whether the reader may attach to the task and whether it has room, and the last attaches the stored file. A
 * file whose attaching fails is removed again.
 *
 * <p>Every method needs somebody signed in, which is why the guard sits on the class. The class is not final
 * because the container wraps it in a proxy, which is a subclass.
 */
@Service
@PreAuthorize("isAuthenticated()")
public class Attachments {

    private final AttachmentRecords records;

    private final MediaLibrary library;

    /**
     * Attaches files through the given rows and library.
     *
     * @param records the rows that attach stored files to tasks
     * @param library the stored files
     */
    public Attachments(AttachmentRecords records, MediaLibrary library) {
        this.records = records;
        this.library = library;
    }

    /**
     * Answers the files attached to a task, in the order they were attached.
     *
     * @param task the reference the task is known by outside the database
     * @return the files as a page shows them, or none where the reader may not see that task
     */
    public List<MediaView> of(UUID task) {
        List<Long> held = this.records.held(task);
        Map<Long, MediaView> drawn = this.library.views(held);
        return held.stream().map(drawn::get).filter(Objects::nonNull).toList();
    }

    /**
     * Attaches a file to a task.
     *
     * @param task   the reference the task is known by outside the database
     * @param upload the file
     * @return why the file was not attached, or nothing where it was
     * @throws IllegalStateException when the reader may not see a task under that reference
     */
    public Optional<AttachmentOutcome> attach(UUID task, Upload upload) {
        AttachmentRecords.Room room = this.records.room(task);
        return room.full() ? Optional.of(AttachmentOutcome.TOO_MANY) : this.stored(room, upload);
    }

    private Optional<AttachmentOutcome> stored(AttachmentRecords.Room room, Upload upload) {
        try {
            this.linked(room, this.library.store(upload, room.actor(), MediaPurpose.ATTACHMENT));
            return Optional.empty();
        } catch (MediaRefusedException exception) {
            return Optional.of(AttachmentOutcome.of(exception.getReason()));
        }
    }

    // A stored file the task could not take, such as one whose task was removed meanwhile, is removed again,
    // so no file is left that nothing attaches.
    private void linked(AttachmentRecords.Room room, MediaObject media) {
        try {
            this.records.linked(room, media);
        } catch (DataAccessException exception) {
            this.library.discard(List.of(media.getId()));
            throw exception;
        }
    }

    /**
     * Takes a file from a task and removes it.
     *
     * @param task  the reference the task is known by outside the database
     * @param media the reference the file is known by
     * @throws IllegalStateException when the reader may not see a task under that reference
     */
    public void detach(UUID task, UUID media) {
        this.records.unlinked(task, media).ifPresent(id -> this.library.discard(List.of(id)));
    }

    /**
     * Removes a task together with the files attached to it. The task goes first, so a removal that fails
     * leaves the task with every file it held.
     *
     * @param task    the reference the task is known by outside the database
     * @param removal what removes the task
     */
    public void removedWith(UUID task, Runnable removal) {
        List<Long> held = this.records.held(task);
        removal.run();
        this.library.discard(held);
    }
}
