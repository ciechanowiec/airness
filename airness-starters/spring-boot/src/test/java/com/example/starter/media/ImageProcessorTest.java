package com.example.starter.media;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class ImageProcessorTest {

    private static final int WIDTH = 640;

    @Test
    void signsAPathAsImgproxyDocumentsIt() {
        HexFormat hex = HexFormat.of();
        // The example the imgproxy documentation signs.
        String path = "/rs:fill:300:400:0/g:sm/aHR0cDovL2V4YW1w/bGUuY29tL2ltYWdl/cy9jdXJpb3NpdHku/anBn.png";
        assertEquals(
            "oKfUtW34Dvo2BGQehJFR4Nr0_rIjOtdtzJ3QFsUcXH8",
            ImageProcessor.signature(hex.parseHex("736563726574"), hex.parseHex("68656C6C6F"), path)
        );
    }

    @Test
    void asksForAStoredFileFittedIntoAWidthInAFormat() {
        StorageSettings storage = new StorageSettings(
            URI.create("http://objects:9000"), "files", "us-east-1",
            new StorageSettings.StorageCredentials("access", "secret")
        );
        try (
            ImageProcessor images = new ImageProcessor(
                new ImagingSettings(URI.create("http://images:8080"), "00", "00"), storage
            )
        ) {
            assertEquals(
                "/rs:fit:640:0/q:50/f:avif/plain/s3://files/media/a/original",
                images.path("media/a/original", WIDTH, ImageFormat.AVIF)
            );
        }
    }
}
