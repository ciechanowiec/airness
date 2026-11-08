package eu.ciechanowiec.airness.governance;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;

/**
 * Decides from its bytes whether an image file would render, chosen by the extension of its name.
 *
 * <p>Each format is read for the structure that a truncated or corrupted copy loses: the markers that
 * open and close it, and the sizes it declares for its own parts. A file with an extension this does not
 * know is not an image it judges. What a format's end marker is followed by is not the image, so the
 * trailer a phone camera appends to a JPEG, a motion photo's video among them, is left alone.
 */
@UtilityClass
final class ImageBytes {

    private static final int JPEG_PREFIX = 0xFF;
    private static final int JPEG_START = 0xD8;
    private static final int JPEG_END = 0xD9;
    private static final int JPEG_MINIMUM = 4;
    // A 0xFF the encoder escaped inside entropy-coded data, which is no marker.
    private static final int JPEG_STUFFED = 0x00;
    private static final int JPEG_RESTART_FIRST = 0xD0;
    private static final int JPEG_RESTART_LAST = 0xD7;
    private static final int JPEG_TEMPORARY = 0x01;
    // The two bytes of the start marker, after which the first segment begins.
    private static final int JPEG_FIRST_SEGMENT = 2;
    private static final int BYTE = 8;
    private static final int NO_MARKER = -1;
    private static final int GIF_HEADER = 6;
    private static final int GIF_TRAILER = 0x3B;
    private static final Set<String> GIF_VERSIONS = Set.of("GIF87a", "GIF89a");
    private static final String RIFF = "RIFF";
    private static final String WEBP = "WEBP";
    private static final int RIFF_TAG = 4;
    private static final int RIFF_FORM = 8;
    private static final int WEBP_HEADER = 12;
    // The size a RIFF container declares leaves out its own tag and size field.
    private static final int RIFF_UNCOUNTED = 8;
    private static final int ICO_HEADER = 6;
    private static final int ICO_TYPE_OFFSET = 2;
    private static final int ICO_COUNT_OFFSET = 4;
    private static final int ICO_ENTRY = 16;
    private static final int ICO_SIZE_OFFSET = 8;
    private static final int ICO_DATA_OFFSET = 12;
    private static final Map<String, Function<byte[], Optional<String>>> VALIDATORS = Map.of(
        "png", PngBytes::problem,
        "jpg", ImageBytes::jpeg,
        "jpeg", ImageBytes::jpeg,
        "gif", ImageBytes::gif,
        "webp", ImageBytes::webp,
        "ico", ImageBytes::ico
    );

    /**
     * Whether a file is an image this reads, by its extension.
     *
     * @param file the file
     * @return whether its extension names a format this validates
     */
    static boolean supported(Path file) {
        return VALIDATORS.containsKey(extension(file));
    }

    /**
     * The reason an image would not render, if it would not.
     *
     * @param file  the file, read only for its extension
     * @param bytes the whole file
     * @return the defect, or nothing when the image is intact or is no image this reads
     */
    static Optional<String> problem(Path file, byte[] bytes) {
        return Optional.ofNullable(VALIDATORS.get(extension(file)))
            .flatMap(validator -> bytes.length == 0 ? Optional.of("empty file") : validator.apply(bytes));
    }

    private static Optional<String> jpeg(byte[] bytes) {
        if (!jpegStart(bytes)) {
            return Optional.of("not a valid JPEG (bad SOI marker)");
        }
        return jpegEnds(bytes) ? Optional.empty() : Optional.of("truncated JPEG (missing EOI marker)");
    }

    // Whether the image reaches its end marker. A segment is stepped over by the length it declares, so the
    // end marker of a thumbnail embedded in its metadata ends nothing, and one running past the file is cut
    // short. Entropy-coded data, and whatever stray bytes stand between two segments, are read up to the next
    // byte pair that is a marker rather than an escaped 0xFF or fill, which covers the several scans a
    // progressive image carries and the restart markers inside a scan.
    private static boolean jpegEnds(byte[] bytes) {
        int marker = nextMarker(bytes, JPEG_FIRST_SEGMENT);
        while (marker != NO_MARKER && unsigned(bytes, marker) != JPEG_END) {
            marker = nextMarker(bytes, standalone(unsigned(bytes, marker)) ? marker + 1 : segmentEnd(bytes, marker));
        }
        return marker != NO_MARKER;
    }

    // The index of the code byte of the first marker whose prefix is at or after the index given.
    private static int nextMarker(byte[] bytes, int from) {
        for (int index = from; index + 1 < bytes.length; index++) {
            int code = unsigned(bytes, index + 1);
            if (unsigned(bytes, index) == JPEG_PREFIX && code != JPEG_PREFIX && code != JPEG_STUFFED) {
                return index + 1;
            }
        }
        return NO_MARKER;
    }

    private static boolean standalone(int code) {
        boolean restart = code >= JPEG_RESTART_FIRST && code <= JPEG_RESTART_LAST;
        return restart || code == JPEG_START || code == JPEG_TEMPORARY;
    }

    // The index after a segment, whose two-byte length counts itself and not the marker before it.
    private static int segmentEnd(byte[] bytes, int code) {
        if (code + 2 >= bytes.length) {
            return bytes.length;
        }
        return code + 1 + ((unsigned(bytes, code + 1) << BYTE) | unsigned(bytes, code + 2));
    }

    private static boolean jpegStart(byte[] bytes) {
        return bytes.length >= JPEG_MINIMUM && unsigned(bytes, 0) == JPEG_PREFIX && unsigned(bytes, 1) == JPEG_START;
    }

    private static Optional<String> gif(byte[] bytes) {
        if (!GIF_VERSIONS.contains(ascii(bytes, 0, GIF_HEADER))) {
            return Optional.of("not a valid GIF (bad header)");
        }
        return unsigned(bytes, bytes.length - 1) == GIF_TRAILER
            ? Optional.empty()
            : Optional.of("truncated GIF (missing trailer byte)");
    }

    private static Optional<String> webp(byte[] bytes) {
        if (!riff(bytes)) {
            return Optional.of("not a valid WebP (bad RIFF/WEBP header)");
        }
        long declared = Integer.toUnsignedLong(little(bytes).getInt(RIFF_TAG)) + RIFF_UNCOUNTED;
        return declared > bytes.length
            ? Optional.of("truncated WebP (RIFF size exceeds file length)")
            : Optional.empty();
    }

    private static boolean riff(byte[] bytes) {
        return bytes.length >= WEBP_HEADER
            && RIFF.equals(ascii(bytes, 0, RIFF_TAG))
            && WEBP.equals(ascii(bytes, RIFF_FORM, WEBP_HEADER));
    }

    private static Optional<String> ico(byte[] bytes) {
        if (!icon(bytes)) {
            return Optional.of("not a valid ICO (bad header)");
        }
        ByteBuffer buffer = little(bytes);
        int count = Short.toUnsignedInt(buffer.getShort(ICO_COUNT_OFFSET));
        return count == 0
            ? Optional.of("invalid ICO (no image entries)")
            : IntStream.range(0, count)
                .mapToObj(index -> icoEntry(buffer, ICO_HEADER + index * ICO_ENTRY))
                .flatMap(Optional::stream)
                .findFirst();
    }

    // A zero reserved field, then an image type of 1, which a cursor does not have.
    private static boolean icon(byte[] bytes) {
        ByteBuffer buffer = little(bytes);
        return bytes.length >= ICO_HEADER
            && buffer.getShort(0) == 0
            && Short.toUnsignedInt(buffer.getShort(ICO_TYPE_OFFSET)) == 1;
    }

    private static Optional<String> icoEntry(ByteBuffer buffer, int entry) {
        if (entry + ICO_ENTRY > buffer.limit()) {
            return Optional.of("truncated ICO (directory runs past end of file)");
        }
        long size = Integer.toUnsignedLong(buffer.getInt(entry + ICO_SIZE_OFFSET));
        long start = Integer.toUnsignedLong(buffer.getInt(entry + ICO_DATA_OFFSET));
        return start + size > buffer.limit()
            ? Optional.of("truncated ICO (image data runs past end of file)")
            : Optional.empty();
    }

    private static ByteBuffer little(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static int unsigned(byte[] bytes, int index) {
        return Byte.toUnsignedInt(bytes[index]);
    }

    private static String ascii(byte[] bytes, int from, int to) {
        int end = Math.min(to, bytes.length);
        return new String(bytes, from, Math.max(end - from, 0), StandardCharsets.ISO_8859_1);
    }

    private static String extension(Path file) {
        String name = String.valueOf(file.getFileName());
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
