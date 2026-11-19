package com.example.starter.media;

import java.util.List;

/**
 * How a stored file is drawn: the size of its widest rendition and every width it was rendered at. A file
 * with no picture has no widths.
 *
 * @param width  the width of the widest rendition, in pixels
 * @param height the height of the widest rendition, in pixels
 * @param widths every width a rendition exists at, narrowest first
 */
public record Rendering(int width, int height, List<Integer> widths) {

    /**
     * How a file with no picture is drawn: not at all.
     */
    public static final Rendering NONE = new Rendering(0, 0, List.of());

    /**
     * Keeps a copy of the widths, so a rendering stays what it was.
     */
    public Rendering {
        widths = List.copyOf(widths);
    }

    /**
     * Answers whether the file has a picture to draw.
     *
     * @return whether any rendition exists
     */
    public boolean drawable() {
        return !this.widths.isEmpty();
    }
}
