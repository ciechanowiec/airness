package com.example.starter.media;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RenditionsTest {

    private static final List<Integer> PORTRAIT_WIDTHS = List.of(64, 128, 256);

    private static final int SIDE = 256;

    private static final int WIDTH = 640;

    @Test
    void keepsTheWidthsOfARenditionInTheirOrder() {
        MediaRendition rendition = new MediaRendition(
            new RenditionKey("ab", MediaPurpose.PORTRAIT), SIDE, SIDE, PORTRAIT_WIDTHS
        );
        assertEquals(PORTRAIT_WIDTHS, rendition.widths());
    }

    @Test
    void writesTheRenditionsOfBytesForAPurposeUnderOnePrefix() {
        RenditionKey key = new RenditionKey("ab", MediaPurpose.ATTACHMENT);
        assertAll(
            () -> assertEquals("renditions/ab/attachment/", key.prefix()),
            () -> assertEquals("renditions/ab/attachment/w640.webp", key.key(WIDTH, ImageFormat.WEBP))
        );
    }

    @Test
    void drawsOnlyAFileThatWasRendered() {
        assertAll(
            () -> assertFalse(Rendering.NONE.drawable()),
            () -> assertTrue(new Rendering(SIDE, SIDE, List.of(SIDE)).drawable())
        );
    }

    @Test
    void readsAnEncodingByItsExtensionAndNoOther() {
        assertAll(
            () -> assertEquals(ImageFormat.AVIF, ImageFormat.of("avif")),
            () -> assertEquals(ImageFormat.WEBP, ImageFormat.of("webp")),
            () -> assertThrows(IllegalArgumentException.class, () -> ImageFormat.of("png"))
        );
    }
}
