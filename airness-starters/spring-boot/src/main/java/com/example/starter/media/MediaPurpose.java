package com.example.starter.media;

import java.util.List;
import java.util.stream.Stream;

/**
 * What a stored file is for, which decides the widths its picture is rendered at. A file attached to a
 * task is drawn from a thumbnail up to a full screen, and a portrait only ever as a small circle, so a
 * portrait is never rendered at widths no page draws it at.
 */
public enum MediaPurpose {

    /**
     * A file attached to a task.
     */
    ATTACHMENT,

    /**
     * The picture an account is shown with.
     */
    PORTRAIT;

    // From a list thumbnail to a full screen at three device pixels per CSS pixel.
    private static final List<Integer> ATTACHMENT_WIDTHS = List.of(320, 640, 960, 1280, 1920);

    // From the smallest avatar at one device pixel per CSS pixel to the largest at three.
    private static final List<Integer> PORTRAIT_WIDTHS = List.of(64, 128, 256);

    private List<Integer> widths() {
        return switch (this) {
            case ATTACHMENT -> ATTACHMENT_WIDTHS;
            case PORTRAIT -> PORTRAIT_WIDTHS;
        };
    }

    /**
     * Answers the widest rendition a picture for this purpose is ever drawn from.
     *
     * @return that width in pixels
     */
    int widest() {
        return this.widths().getLast();
    }

    /**
     * Answers the widths a picture whose widest rendition is the given width is rendered at: every width of
     * this purpose narrower than it, and that width itself, so no picture is ever enlarged.
     *
     * @param widest the width of the widest rendition, which is never wider than {@link #widest()}
     * @return the widths, narrowest first
     */
    List<Integer> widthsUpTo(int widest) {
        return Stream.concat(this.widths().stream().filter(width -> width < widest), Stream.of(widest)).toList();
    }
}
