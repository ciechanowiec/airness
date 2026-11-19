package com.example.starter.media;

/**
 * What a stored file is, which decides how it is drawn: an image is drawn from its renditions, and a PDF
 * from the renditions of its first page, where that page could be read.
 */
public enum MediaKind {

    /**
     * A picture: JPEG, PNG, WebP, AVIF or HEIC.
     */
    IMAGE,

    /**
     * A PDF document.
     */
    PDF
}
