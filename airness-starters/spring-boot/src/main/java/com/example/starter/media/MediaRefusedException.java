package com.example.starter.media;

import java.io.Serial;
import lombok.Getter;

/**
 * Says why a file was not stored. Nothing of a refused file is kept.
 */
@Getter
public final class MediaRefusedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Why the file was refused.
     */
    private final Reason reason;

    /**
     * Refuses a file for the given reason.
     *
     * @param reason why the file was refused
     */
    public MediaRefusedException(Reason reason) {
        super("The file was refused: " + reason);
        this.reason = reason;
    }

    /**
     * Why a file is refused.
     */
    public enum Reason {

        /**
         * The file holds nothing.
         */
        EMPTY,

        /**
         * The file holds more than the application keeps.
         */
        TOO_LARGE,

        /**
         * The file is none of the kinds the application draws.
         */
        UNSUPPORTED
    }
}
