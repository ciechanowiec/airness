package com.example.starter.demo;

import com.example.starter.identity.Owners;
import com.example.starter.identity.Portraits;
import com.example.starter.media.Upload;
import com.example.starter.task.Attachments;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The pictures of the demonstration, which ship with the application under {@code demo/} and are stored the
 * way any other picture is: each person's portrait, and one picture attached to each task. They are read
 * from the classpath here, so the demonstration needs nothing but the archive and its services.
 */
@Component
public final class DemoSeeds {

    private final Attachments attachments;

    private final Portraits portraits;

    private final Owners owners;

    private final Clock clock;

    /**
     * Stores the pictures of the demonstration through the given attachments and portraits.
     *
     * @param attachments the files attached to tasks
     * @param portraits   the pictures the accounts are shown with
     * @param owners      the accounts, which know the reader's own
     * @param clock       the clock the day the data is written on is read from
     */
    public DemoSeeds(Attachments attachments, Portraits portraits, Owners owners, Clock clock) {
        this.attachments = attachments;
        this.portraits = portraits;
        this.owners = owners;
        this.clock = clock;
    }

    /**
     * Answers the day the data is written on.
     *
     * @return that day, in the zone the application keeps
     */
    LocalDate today() {
        return LocalDate.now(this.clock);
    }

    /**
     * Gives the reader the portrait the demonstration ships for them.
     *
     * @param picture the path of the picture under the classpath
     */
    void portray(String picture) {
        this.portraits.change(this.owners.reader(), upload(picture));
    }

    /**
     * Attaches the picture the demonstration ships for a task to that task.
     *
     * @param task    the reference of the task
     * @param picture the path of the picture under the classpath
     */
    void attach(UUID task, String picture) {
        this.attachments.attach(task, upload(picture));
    }

    private static Upload upload(String picture) {
        return new Upload(picture.substring(picture.lastIndexOf('/') + 1), new ClassPathResource(picture));
    }
}
