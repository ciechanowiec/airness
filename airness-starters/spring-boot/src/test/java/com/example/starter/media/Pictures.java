package com.example.starter.media;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

/**
 * Files the tests store, drawn here rather than kept beside the tests, so each is exactly as large as the test
 * that stores it needs.
 */
@UtilityClass
public final class Pictures {

    private static final Color INK = new Color(0x43, 0x38, 0xCA);

    private static final Color PAPER = new Color(0xF5, 0xF5, 0xF7);

    /**
     * Answers a JPEG of the given size, split into two colours so it is no flat field.
     *
     * @param width  its width in pixels
     * @param height its height in pixels
     * @return its bytes
     */
    public static byte[] jpeg(int width, int height) {
        return encoded(width, height, "jpeg");
    }

    /**
     * Answers a PNG of the given size.
     *
     * @param width  its width in pixels
     * @param height its height in pixels
     * @return its bytes
     */
    public static byte[] png(int width, int height) {
        return encoded(width, height, "png");
    }

    /**
     * Answers a PDF of one A4 page.
     *
     * @return its bytes
     */
    @SneakyThrows
    public static byte[] pdf() {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            document.addPage(new PDPage(PDRectangle.A4));
            document.save(bytes);
            return bytes.toByteArray();
        }
    }

    /**
     * Answers a PDF that holds no page at all.
     *
     * @return its bytes
     */
    @SneakyThrows
    public static byte[] pagelessPdf() {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            document.save(bytes);
            return bytes.toByteArray();
        }
    }

    @SneakyThrows
    private static byte[] encoded(int width, int height, String format) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D canvas = image.createGraphics();
        canvas.setColor(INK);
        canvas.fillRect(0, 0, width, height);
        canvas.setColor(PAPER);
        canvas.fillRect(0, 0, width / 2, height);
        canvas.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, format, bytes);
        return bytes.toByteArray();
    }
}
