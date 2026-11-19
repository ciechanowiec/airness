package com.example.starter.media;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class MediaTypesTest {

    // An ISO base media file opens with the four-byte size of its first box, then the box type and the brand.
    private static final int BOX_OFFSET = 4;

    private static final int BOX_AND_BRAND = 8;

    private static final int SIDE = 4;

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    // An ISO base media file opens with the size of its first box, then the box type and the major brand.
    private static byte[] boxed(String brand) {
        byte[] head = new byte[MediaTypes.HEAD];
        System.arraycopy(ascii("ftyp" + brand), 0, head, BOX_OFFSET, BOX_AND_BRAND);
        return head;
    }

    @Test
    void tellsAJpeg() {
        assertEquals(
            Optional.of(new Detected(MediaKind.IMAGE, "image/jpeg")), MediaTypes.of(Pictures.jpeg(SIDE, SIDE))
        );
    }

    @Test
    void tellsAPng() {
        assertEquals(Optional.of(new Detected(MediaKind.IMAGE, "image/png")), MediaTypes.of(Pictures.png(SIDE, SIDE)));
    }

    @Test
    void tellsAWebp() {
        assertEquals(
            Optional.of(new Detected(MediaKind.IMAGE, "image/webp")), MediaTypes.of(ascii("RIFF\0\0\0\0WEBPVP8 "))
        );
    }

    @Test
    void tellsAPdf() {
        assertEquals(Optional.of(new Detected(MediaKind.PDF, "application/pdf")), MediaTypes.of(Pictures.pdf()));
    }

    @Test
    void tellsAnImageByItsBrand() {
        Map<String, String> brands = Map.of(
            "avif", "image/avif", "avis", "image/avif", "heic", "image/heic", "heix", "image/heic",
            "hevc", "image/heic", "hevx", "image/heic", "mif1", "image/heif", "msf1", "image/heif"
        );
        assertAll(
            brands.entrySet().stream().map(
                brand -> () -> assertEquals(
                    Optional.of(new Detected(MediaKind.IMAGE, brand.getValue())), MediaTypes.of(boxed(brand.getKey()))
                )
            )
        );
    }

    @Test
    void refusesAFileItDoesNotDraw() {
        assertAll(
            Stream.of("GIF89a\0\0\0\0\0\0", "<svg xmlns='h", "plain text, called a .jpg", "RIFF\0\0\0\0WAVE", "")
                .map(head -> () -> assertEquals(Optional.empty(), MediaTypes.of(ascii(head))))
        );
    }

    @Test
    void refusesABoxOfAnotherBrand() {
        assertEquals(Optional.empty(), MediaTypes.of(boxed("isom")));
    }

    @Test
    void refusesABoxCutShortBeforeItsBrand() {
        assertEquals(Optional.empty(), MediaTypes.of(ascii("\0\0\0\0ftyp")));
    }
}
