package com.example.starter.media;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * What a stored file is: the name it was handed over under, what its bytes say it is, how many there are,
 * and their SHA-256, which names the bytes whatever the file was called.
 *
 * @param originalName the name the file was handed over under
 * @param kind         how the file is drawn
 * @param contentType  the media type the bytes say they are
 * @param byteSize     how many bytes the file holds
 * @param sha256       the SHA-256 of the bytes, in lowercase hexadecimal
 */
@Embeddable
public record MediaFile(
    @Column(name = "original_name", nullable = false, length = NAME) String originalName,

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    MediaKind kind,

    @Column(name = "content_type", nullable = false) String contentType,

    @Column(name = "byte_size", nullable = false) long byteSize,

    @Column(name = "sha256", nullable = false, length = DIGEST) String sha256
) {

    /**
     * The longest name a file is kept under, which the migration gave the column.
     */
    public static final int NAME = 255;

    private static final int DIGEST = 64;
}
