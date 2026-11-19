package com.example.starter.task;

import com.example.starter.media.MediaRefusedException;
import com.example.starter.web.Named;
import java.util.Arrays;
import java.util.Optional;

/**
 * Why a file somebody tried to attach was not attached. The reason travels in the address of the task's page
 * as one of these constants, so an address can name only a sentence the application already says.
 */
public enum AttachmentOutcome implements Named {

    /**
     * No file was chosen.
     */
    NONE_CHOSEN("attachments.refused.none"),

    /**
     * The file holds nothing.
     */
    EMPTY("attachments.refused.empty"),

    /**
     * The file holds more than the application keeps.
     */
    TOO_LARGE("attachments.refused.large"),

    /**
     * The file is none of the kinds the application draws.
     */
    UNSUPPORTED("attachments.refused.kind"),

    /**
     * The task holds as many files as a task may.
     */
    TOO_MANY("attachments.refused.many");

    /**
     * The name of the query parameter a refusal travels in.
     */
    public static final String PARAMETER = "refused";

    private final String key;

    AttachmentOutcome(String key) {
        this.key = key;
    }

    /**
     * Answers the refusal a constant name stands for.
     *
     * @param name the name of the constant, as the address carries it
     * @return the refusal of that name, and nothing where none carries it
     */
    public static Optional<AttachmentOutcome> named(String name) {
        return Arrays.stream(values()).filter(outcome -> outcome.name().equals(name)).findFirst();
    }

    /**
     * Answers the refusal the library's reason for refusing a file stands for.
     *
     * @param reason why the library refused the file
     * @return the refusal a page tells the reader
     */
    static AttachmentOutcome of(MediaRefusedException.Reason reason) {
        return switch (reason) {
            case EMPTY -> EMPTY;
            case TOO_LARGE -> TOO_LARGE;
            case UNSUPPORTED -> UNSUPPORTED;
        };
    }

    @Override
    public String key() {
        return this.key;
    }
}
