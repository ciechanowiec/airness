package com.example.starter.media;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class MediaObjectTest {

    private static MediaObject stored() {
        return new MediaObject(
            new MediaFile("a.png", MediaKind.IMAGE, "image/png", 1, "ab"), MediaPurpose.ATTACHMENT, 1, Instant.EPOCH
        );
    }

    @Test
    void isNoOtherFileThanItself() {
        MediaObject file = stored();
        Object reference = file.getReference();
        assertAll(
            () -> assertEquals(file.getReference().hashCode(), file.hashCode()),
            () -> assertNotEquals(stored(), file),
            () -> assertNotEquals(file, reference)
        );
    }
}
