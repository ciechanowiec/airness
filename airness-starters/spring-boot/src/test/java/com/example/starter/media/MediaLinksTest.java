package com.example.starter.media;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MediaLinksTest {

    private static final UUID REFERENCE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final String BASE = "/media/00000000-0000-0000-0000-000000000001/";

    private static final Rendering WIDE = new Rendering(1000, 750, List.of(320, 960, 1000));

    private static final int THUMBNAIL = 400;

    private static final Rendering NARROW = new Rendering(500, 375, List.of(320, 500));

    private static MediaView drawn(Rendering rendering) {
        return new MediaView(REFERENCE, "photo.jpg", MediaKind.IMAGE, rendering);
    }

    @Test
    void listsEveryRenditionOfAnEncodingAsASourceSet() {
        assertEquals(
            BASE + "w320.avif 320w, " + BASE + "w500.avif 500w", new MediaLinks().srcset(drawn(NARROW), "avif")
        );
    }

    @Test
    void leavesOutOfASourceSetEveryRenditionWiderThanAThumbnailIsDrawn() {
        assertEquals(BASE + "w320.webp 320w", new MediaLinks().srcset(drawn(NARROW), "webp", THUMBNAIL));
    }

    @Test
    void fallsBackToTheNarrowestWebpRenditionAtLeast640Wide() {
        MediaLinks links = new MediaLinks();
        assertAll(
            () -> assertEquals(BASE + "w960.webp", links.fallback(drawn(WIDE))),
            () -> assertEquals(BASE + "w500.webp", links.fallback(drawn(NARROW))),
            () -> assertEquals(BASE + "w640.webp", links.fallback(drawn(Rendering.NONE)))
        );
    }

    @Test
    void downloadsAFileAsItWasHandedOver() {
        assertEquals(BASE + "original", new MediaLinks().original(drawn(NARROW)));
    }
}
