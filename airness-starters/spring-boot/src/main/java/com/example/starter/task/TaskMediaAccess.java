package com.example.starter.task;

import com.example.starter.TransactionTimeout;
import com.example.starter.media.MediaAccess;
import com.example.starter.media.MediaObject;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lets a reader see a file attached to a task they may see, which is every task for an administrator and
 * their own for anybody else.
 *
 * <p>The class is not final because the container wraps it in a proxy, which is a subclass.
 */
@Component
public class TaskMediaAccess implements MediaAccess {

    private final TaskAttachmentRepository attachments;

    private final TaskRepository tasks;

    private final TaskReach reach;

    /**
     * Decides through the given attachments and tasks.
     *
     * @param attachments the files attached to tasks
     * @param tasks       the tasks
     * @param reach       which tasks the reader may see
     */
    public TaskMediaAccess(TaskAttachmentRepository attachments, TaskRepository tasks, TaskReach reach) {
        this.attachments = attachments;
        this.tasks = tasks;
        this.reach = reach;
    }

    @Override
    @Transactional(readOnly = true, timeout = TransactionTimeout.SECONDS)
    public boolean permits(MediaObject media) {
        return this.attachments.findByMediaId(media.getId())
            .map(
                attachment -> this.tasks.exists(
                    this.reach.visible().and(TaskCriteria.identified(attachment.getTaskId()))
                )
            ).orElse(false);
    }
}
