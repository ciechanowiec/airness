package com.example.starter.media;

/**
 * An encoding a rendition is written in. AVIF is the smaller of the two and WebP the one every browser
 * draws, so a page offers both and the browser takes the first it can. Each is encoded at the quality that
 * keeps a photograph free of visible artefacts.
 */
public enum ImageFormat {

    /**
     * AV1 Image File Format.
     */
    AVIF("avif", "image/avif", 50),

    /**
     * WebP.
     */
    WEBP("webp", "image/webp", 75);

    private final String extension;

    private final String contentType;

    private final int quality;

    ImageFormat(String extension, String contentType, int quality) {
        this.extension = extension;
        this.contentType = contentType;
        this.quality = quality;
    }

    /**
     * Answers the format a file extension names.
     *
     * @param extension the extension, such as {@code avif}
     * @return the format
     * @throws IllegalArgumentException when no format has that extension
     */
    static ImageFormat of(String extension) {
        return switch (extension) {
            case "avif" -> AVIF;
            case "webp" -> WEBP;
            case null, default -> throw new IllegalArgumentException("No image format has the extension " + extension);
        };
    }

    /**
     * Answers the extension the format is written under and asked for by.
     *
     * @return the extension, such as {@code avif}
     */
    public String extension() {
        return this.extension;
    }

    String contentType() {
        return this.contentType;
    }

    int quality() {
        return this.quality;
    }
}
