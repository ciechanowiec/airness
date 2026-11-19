package com.example.starter.media;

import java.util.UUID;

/**
 * A stored file as a page shows it.
 *
 * @param reference the reference the file is served under
 * @param name      the name it was handed over under
 * @param kind      what it is
 * @param rendering how its picture is drawn
 */
public record MediaView(UUID reference, String name, MediaKind kind, Rendering rendering) {

    /**
     * What stands in for a file where there is none, such as the cover of a task with no picture.
     */
    public static final MediaView NONE = new MediaView(new UUID(0, 0), "", MediaKind.IMAGE, Rendering.NONE);
}
