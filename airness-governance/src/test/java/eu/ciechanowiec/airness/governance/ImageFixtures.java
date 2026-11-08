package eu.ciechanowiec.airness.governance;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.Deflater;
import lombok.experimental.UtilityClass;

/**
 * Builds the smallest structurally complete file of each image format, and the damaged copies of it.
 *
 * <p>The bytes are assembled from each format's own layout rather than read from checked-in images, so a
 * test names the one part it damages and the rest stays intact by construction.
 */
@UtilityClass
final class ImageFixtures {

    private static final int HIGH_BIT = 0x89;
    private static final int END_OF_FILE = 0x1A;
    private static final byte[] PNG_SIGNATURE = {
        (byte) HIGH_BIT, 'P', 'N', 'G', '\r', '\n', (byte) END_OF_FILE, '\n'
    };
    // The image header carries width, height, depth, colour type, compression, filter and interlace.
    private static final int HEADER_LENGTH = 13;
    private static final int MARKER = 0xFF;
    private static final int JPEG_START = 0xD8;
    private static final int JPEG_END = 0xD9;
    private static final int JPEG_COMMENT = 0xFE;
    private static final int JPEG_SCAN = 0xDA;
    private static final int JPEG_RESTART = 0xD0;
    // Any byte but the marker byte is entropy-coded data, so the value stands for every such byte.
    private static final byte ENTROPY_CODED = 0x12;
    // A baseline scan selects the coefficients 0 to 63.
    private static final byte LAST_COEFFICIENT = 0x3F;
    private static final int BYTE = 8;
    private static final int GIF_TRAILER = 0x3B;
    // Room for a stream that does not compress at all, plus the zlib header and checksum.
    private static final int DEFLATE_SLACK = 64;
    private static final int WORD = 4;
    private static final int ICO_ENTRY = 16;
    private static final int ICO_HEADER = 6;
    private static final int PIXEL_ROW = 4;

    static byte[] png() {
        return png(deflated(new byte[PIXEL_ROW]));
    }

    static byte[] png(byte[] imageData) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(PNG_SIGNATURE);
        out.writeBytes(chunk("IHDR", new byte[HEADER_LENGTH]));
        out.writeBytes(chunk("IDAT", imageData));
        out.writeBytes(chunk("IEND", new byte[0]));
        return out.toByteArray();
    }

    static byte[] pngWithoutImageData() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(PNG_SIGNATURE);
        out.writeBytes(chunk("IHDR", new byte[HEADER_LENGTH]));
        out.writeBytes(chunk("IEND", new byte[0]));
        return out.toByteArray();
    }

    static byte[] jpeg() {
        return joined(jpegStart(), jpegComment(), jpegScan(), jpegEnd());
    }

    static byte[] jpegStart() {
        return new byte[] {(byte) MARKER, (byte) JPEG_START};
    }

    static byte[] jpegEnd() {
        return new byte[] {(byte) MARKER, (byte) JPEG_END};
    }

    static byte[] jpegComment() {
        return jpegSegment(JPEG_COMMENT, "Airness".getBytes(StandardCharsets.ISO_8859_1));
    }

    // A segment's length counts its own two bytes and the body, never the marker.
    static byte[] jpegSegment(int marker, byte[] body) {
        int length = body.length + 2;
        return joined(new byte[] {(byte) MARKER, (byte) marker, (byte) (length >> BYTE), (byte) length}, body);
    }

    // A scan header, then entropy-coded data holding an escaped 0xFF and a restart marker, then fill bytes.
    static byte[] jpegScan() {
        byte[] data = {
            ENTROPY_CODED, (byte) MARKER, 0x00, ENTROPY_CODED, (byte) MARKER, (byte) JPEG_RESTART, ENTROPY_CODED,
            (byte) MARKER
        };
        return joined(jpegSegment(JPEG_SCAN, new byte[] {0x01, 0x01, 0x00, 0x00, LAST_COEFFICIENT, 0x00}), data);
    }

    static byte[] joined(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Arrays.stream(parts).forEach(out::writeBytes);
        return out.toByteArray();
    }

    static byte[] gif() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("GIF89a".getBytes(StandardCharsets.ISO_8859_1));
        out.writeBytes(new byte[] {0x01, 0x00, GIF_TRAILER});
        return out.toByteArray();
    }

    static byte[] webp() {
        byte[] body = "WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);
        ByteBuffer buffer = ByteBuffer.allocate(WORD + WORD + body.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.ISO_8859_1));
        buffer.putInt(body.length);
        buffer.put(body);
        return buffer.array();
    }

    static byte[] ico() {
        byte[] image = {0x01, 0x02, 0x03, 0x04};
        ByteBuffer buffer = ByteBuffer.allocate(ICO_HEADER + ICO_ENTRY + image.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) 0).putShort((short) 1).putShort((short) 1);
        buffer.put(new byte[ICO_ENTRY - WORD - WORD]);
        buffer.putInt(image.length).putInt(ICO_HEADER + ICO_ENTRY);
        buffer.put(image);
        return buffer.array();
    }

    static byte[] icoWithoutEntries() {
        return ByteBuffer.allocate(ICO_HEADER).order(ByteOrder.LITTLE_ENDIAN)
            .putShort((short) 0).putShort((short) 1).putShort((short) 0).array();
    }

    static byte[] withoutLast(byte[] bytes, int count) {
        return Arrays.copyOf(bytes, bytes.length - count);
    }

    static byte[] withByte(byte[] bytes, int index, int value) {
        byte[] copy = bytes.clone();
        copy[index] = (byte) value;
        return copy;
    }

    static byte[] deflated(byte[] raw) {
        try (Deflater deflater = new Deflater()) {
            deflater.setInput(raw);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[raw.length + DEFLATE_SLACK];
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer));
            }
            return out.toByteArray();
        }
    }

    private static byte[] chunk(String type, byte[] payload) {
        return ByteBuffer.allocate(WORD + WORD + payload.length + WORD)
            .putInt(payload.length)
            .put(type.getBytes(StandardCharsets.ISO_8859_1))
            .put(payload)
            .putInt(0)
            .array();
    }
}
