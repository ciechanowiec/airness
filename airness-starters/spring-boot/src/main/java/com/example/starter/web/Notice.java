package com.example.starter.web;

import java.util.Arrays;
import java.util.Optional;

/**
 * What a page tells a reader it was sent on to after a change, such as that a task was saved. A notice
 * travels in the address of the page as one of these constants, so an address can name only a sentence
 * the application already says.
 */
public enum Notice implements Named {

    /**
     * A record was written.
     */
    CREATED("notice.created"),

    /**
     * A record was changed.
     */
    SAVED("notice.saved"),

    /**
     * A record was removed.
     */
    DELETED("notice.deleted"),

    /**
     * An account may sign in again.
     */
    ENABLED("notice.enabled"),

    /**
     * An account may no longer sign in.
     */
    DISABLED("notice.disabled"),

    /**
     * The reader's password was changed.
     */
    PASSWORD_CHANGED("notice.passwordChanged");

    /**
     * The name of the query parameter a notice travels in.
     */
    public static final String PARAMETER = "notice";

    private final String key;

    Notice(String key) {
        this.key = key;
    }

    /**
     * Answers the notice a constant name stands for.
     *
     * @param name the name of the constant, as the address carries it
     * @return the notice of that name, and nothing where none carries it
     */
    public static Optional<Notice> named(String name) {
        return Arrays.stream(values()).filter(notice -> notice.name().equals(name)).findFirst();
    }

    /**
     * Answers the redirect that sends a reader on to the given path, telling them this notice.
     *
     * @param path the path of this application the reader is sent on to
     * @return the view name of that redirect
     */
    public String redirect(String path) {
        return "redirect:%s?%s=%s".formatted(path, PARAMETER, this.name());
    }

    @Override
    public String key() {
        return this.key;
    }
}
