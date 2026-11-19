package com.example.starter.media;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class MediaPurposeTest {

    private static final List<Integer> ATTACHMENT_WIDTHS = List.of(320, 640, 960, 1280, 1920);

    private static final int WIDEST_ATTACHMENT = 1920;

    private static final int WIDEST_PORTRAIT = 256;

    private static final int NARROWER = 1000;

    private static final List<Integer> NARROWER_WIDTHS = List.of(320, 640, 960, 1000);

    private static final int NARROWEST = 200;

    private static final int SMALL_PORTRAIT = 100;

    private static final List<Integer> SMALL_PORTRAIT_WIDTHS = List.of(64, 100);

    @Test
    void rendersAWidePictureAtEveryWidthOfItsPurpose() {
        assertEquals(ATTACHMENT_WIDTHS, MediaPurpose.ATTACHMENT.widthsUpTo(WIDEST_ATTACHMENT));
    }

    @Test
    void rendersANarrowerPictureUpToItsOwnWidthAndNoWider() {
        assertAll(
            () -> assertEquals(NARROWER_WIDTHS, MediaPurpose.ATTACHMENT.widthsUpTo(NARROWER)),
            () -> assertEquals(List.of(NARROWEST), MediaPurpose.ATTACHMENT.widthsUpTo(NARROWEST)),
            () -> assertEquals(SMALL_PORTRAIT_WIDTHS, MediaPurpose.PORTRAIT.widthsUpTo(SMALL_PORTRAIT))
        );
    }

    @Test
    void drawsAPortraitNoWiderThanAnyPageDrawsIt() {
        assertAll(
            () -> assertEquals(WIDEST_PORTRAIT, MediaPurpose.PORTRAIT.widest()),
            () -> assertEquals(WIDEST_ATTACHMENT, MediaPurpose.ATTACHMENT.widest())
        );
    }
}
