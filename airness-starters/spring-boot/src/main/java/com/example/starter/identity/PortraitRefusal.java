package com.example.starter.identity;

import com.example.starter.media.MediaRefusedException;
import com.example.starter.web.Named;
import java.util.Arrays;
import java.util.Optional;

/**
 * Why a picture somebody chose for their portrait was not taken. The reason travels in the address of the
 * account page as one of these constants, so an address can name only a sentence the application already says.
 */
public enum PortraitRefusal implements Named {

    /**
     * No picture was chosen.
     */
    NONE_CHOSEN("account.portrait.refused.none"),

    /**
     * The picture holds nothing.
     */
    EMPTY("account.portrait.refused.empty"),

    /**
     * The picture holds more than the application keeps.
     */
    TOO_LARGE("account.portrait.refused.large"),

    /**
     * The file is no picture the application draws.
     */
    UNSUPPORTED("account.portrait.refused.kind");

    /**
     * The name of the query parameter a refusal travels in.
     */
    public static final String PARAMETER = "portrait";

    private final String key;

    PortraitRefusal(String key) {
        this.key = key;
    }

    /**
     * Answers the refusal a constant name stands for.
     *
     * @param name the name of the constant, as the address carries it
     * @return the refusal of that name, and nothing where none carries it
     */
    public static Optional<PortraitRefusal> named(String name) {
        return Arrays.stream(values()).filter(refusal -> refusal.name().equals(name)).findFirst();
    }

    /**
     * Answers the refusal the library's reason for refusing a file stands for.
     *
     * @param reason why the library refused the file
     * @return the refusal the page tells the reader
     */
    static PortraitRefusal of(MediaRefusedException.Reason reason) {
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
