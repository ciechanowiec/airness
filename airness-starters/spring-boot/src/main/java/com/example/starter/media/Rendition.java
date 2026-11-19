package com.example.starter.media;

/**
 * One rendition as the renderer answered it: the encoded picture and its size. The bytes are copied on the way
 * in and out, so a rendition stays what the renderer answered.
 */
final class Rendition {

    private final byte[] bytes;

    private final int width;

    private final int height;

    /**
     * Keeps a rendition the renderer answered.
     *
     * @param bytes  the encoded picture
     * @param width  its width in pixels
     * @param height its height in pixels
     */
    Rendition(byte[] bytes, int width, int height) {
        this.bytes = bytes.clone();
        this.width = width;
        this.height = height;
    }

    byte[] bytes() {
        return this.bytes.clone();
    }

    int width() {
        return this.width;
    }

    int height() {
        return this.height;
    }
}
