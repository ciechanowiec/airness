package com.example.starter.media;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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

    private static final byte[] EXIF_HEADER = {'E', 'x', 'i', 'f', 0, 0};

    // The marker of the first application segment, which is where a camera writes its Exif block.
    private static final byte[] APPLICATION_SEGMENT = {(byte) 0xFF, (byte) 0xE1};

    private static final int START_OF_IMAGE = 2;

    // The marker and the length that open a segment.
    private static final int SEGMENT_HEAD = APPLICATION_SEGMENT.length + Short.BYTES;

    private static final int ENTRY = 12;

    private static final int TIFF_MAGIC = 42;

    private static final int ORIENTATION_TAG = 0x0112;

    private static final int ARTIST_TAG = 0x013B;

    private static final int ASCII_TYPE = 2;

    private static final int SHORT_TYPE = 3;

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
     * Answers a JPEG of the given size that carries an Exif block, as a camera writes one: an orientation tag
     * saying how the picture is turned, and the name of whoever took it, which stands for everything private
     * a camera writes, such as where the picture was taken.
     *
     * @param width       its width in pixels, as stored
     * @param height      its height in pixels, as stored
     * @param orientation the Exif orientation, where 6 means the stored picture is turned a quarter to the left
     * @param artist      the name written into the block
     * @return its bytes
     */
    public static byte[] taggedJpeg(int width, int height, int orientation, String artist) {
        byte[] plain = jpeg(width, height);
        byte[] block = exif(orientation, artist);
        ByteBuffer tagged = ByteBuffer.allocate(plain.length + SEGMENT_HEAD + block.length);
        // The block is written straight after the start of image marker, where a camera writes it.
        tagged.put(plain, 0, START_OF_IMAGE)
            .put(APPLICATION_SEGMENT)
            .putShort((short) (block.length + Short.BYTES))
            .put(block)
            .put(plain, START_OF_IMAGE, plain.length - START_OF_IMAGE);
        return tagged.array();
    }

    // An Exif block of two big-endian entries: the orientation, held in the entry itself, and the artist,
    // whose text follows the directory.
    private static byte[] exif(int orientation, String artist) {
        byte[] text = (artist + '\0').getBytes(StandardCharsets.US_ASCII);
        int directory = 8;
        int entries = 2;
        int textOffset = directory + Short.BYTES + entries * ENTRY + Integer.BYTES;
        ByteBuffer block = ByteBuffer.allocate(EXIF_HEADER.length + textOffset + text.length);
        block.put(EXIF_HEADER)
            .put((byte) 'M')
            .put((byte) 'M')
            .putShort((short) TIFF_MAGIC)
            .putInt(directory)
            .putShort((short) entries)
            .putShort((short) ORIENTATION_TAG)
            .putShort((short) SHORT_TYPE)
            .putInt(1)
            .putShort((short) orientation)
            .putShort((short) 0)
            .putShort((short) ARTIST_TAG)
            .putShort((short) ASCII_TYPE)
            .putInt(text.length)
            .putInt(textOffset)
            .putInt(0)
            .put(text);
        return block.array();
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
    public static byte[] pdf() {
        return pdf(PDRectangle.A4);
    }

    /**
     * Answers a PDF of one page of the given size.
     *
     * @param width  the width of the page, in points
     * @param height the height of the page, in points
     * @return its bytes
     */
    public static byte[] pdf(float width, float height) {
        return pdf(new PDRectangle(width, height));
    }

    @SneakyThrows
    private static byte[] pdf(PDRectangle box) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            document.addPage(new PDPage(box));
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
