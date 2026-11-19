package com.example.starter.task;

import com.example.starter.media.MediaKind;
import com.example.starter.media.MediaLibrary;
import com.example.starter.media.MediaView;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

/**
 * The picture a list or a board shows a task by: the first image attached to it. The covers of a page are
 * read in two queries whatever its length, one for the attachments and one for how they are drawn.
 */
@Component
public final class TaskCovers {

    private final TaskAttachmentRepository attachments;

    private final MediaLibrary library;

    /**
     * Reads the covers through the given attachments and library.
     *
     * @param attachments the files attached to tasks
     * @param library     the stored files, and how they are drawn
     */
    public TaskCovers(TaskAttachmentRepository attachments, MediaLibrary library) {
        this.attachments = attachments;
        this.library = library;
    }

    /**
     * Answers the covers of the given tasks.
     *
     * @param tasks the identifiers of the tasks
     * @return the cover of each task that has one, under the identifier of the task
     */
    Map<Long, MediaView> of(Collection<Long> tasks) {
        if (tasks.isEmpty()) {
            return Map.of();
        }
        List<TaskAttachment> covers = this.attachments.covers(tasks, MediaKind.IMAGE, Limit.of(tasks.size()));
        Map<Long, MediaView> drawn = this.library.views(covers.stream().map(TaskAttachment::getMediaId).toList());
        return covers.stream()
            .collect(
                Collectors.toMap(
                    TaskAttachment::getTaskId, cover -> drawn.getOrDefault(cover.getMediaId(), MediaView.NONE)
                )
            );
    }
}
