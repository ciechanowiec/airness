package com.example.starter.media;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Locale;

/**
 * Which renditions are meant: those of the bytes with the given SHA-256, drawn for the given purpose.
 *
 * @param sha256  the SHA-256 of the stored bytes
 * @param purpose what the renditions are drawn for
 */
@Embeddable
public record RenditionKey(
    @Column(name = "sha256", nullable = false, length = 64) String sha256,

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false)
    MediaPurpose purpose
) {

    /**
     * Answers the prefix every rendition of these bytes for this purpose is written under.
     *
     * @return the prefix, ending with a slash
     */
    String prefix() {
        return "renditions/%s/%s/".formatted(this.sha256, this.purpose.name().toLowerCase(Locale.ROOT));
    }

    /**
     * Answers the key of one rendition.
     *
     * @param width  its width in pixels
     * @param format its encoding
     * @return the key it is written under
     */
    String key(int width, ImageFormat format) {
        return "%sw%d.%s".formatted(this.prefix(), width, format.extension());
    }
}
