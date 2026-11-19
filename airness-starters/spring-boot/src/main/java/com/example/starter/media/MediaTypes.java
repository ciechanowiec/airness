package com.example.starter.media;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Tells what a file is from its first bytes, never from its name or from what the browser said it is, since
 * both are written by whoever sends the file. A file that is none of the kinds the application draws is
 * refused rather than stored.
 */
@UtilityClass
final class MediaTypes {

    /**
     * How many leading bytes the detection reads.
     */
    static final int HEAD = 12;

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private static final byte[] PDF = ascii("%PDF-");

    private static final byte[] RIFF = ascii("RIFF");

    private static final byte[] WEBP = ascii("WEBP");

    private static final byte[] BOX = ascii("ftyp");

    private static final int BOX_OFFSET = 4;

    private static final int BRAND_OFFSET = 8;

    private static final int BRAND_LENGTH = 4;

    // The major brand of an ISO base media file names what the file holds. AVIF and HEIC are both such files,
    // and a still image written by a generic HEIF encoder carries the generic brands.
    private static final Map<String, String> BRANDS = Map.of(
        "avif", "image/avif",
        "avis", "image/avif",
        "heic", "image/heic",
        "heix", "image/heic",
        "hevc", "image/heic",
        "hevx", "image/heic",
        "mif1", "image/heif",
        "msf1", "image/heif"
    );

    /**
     * Answers what the file that starts with the given bytes is.
     *
     * @param head the first bytes of the file, at least {@link #HEAD} of them where the file is that long
     * @return what the file is, and nothing where it is none of the kinds the application draws
     */
    static Optional<Detected> of(byte[] head) {
        Optional<Detected> signed = Stream.of(
            new Signature(starts(head, 0, JPEG), new Detected(MediaKind.IMAGE, "image/jpeg")),
            new Signature(starts(head, 0, PNG), new Detected(MediaKind.IMAGE, "image/png")),
            new Signature(
                starts(head, 0, RIFF) && starts(head, BRAND_OFFSET, WEBP), new Detected(MediaKind.IMAGE, "image/webp")
            ),
            new Signature(starts(head, 0, PDF), new Detected(MediaKind.PDF, "application/pdf"))
        ).filter(Signature::matches).map(Signature::detected).findFirst();
        return signed.or(() -> brand(head).map(BRANDS::get).map(type -> new Detected(MediaKind.IMAGE, type)));
    }

    private static Optional<String> brand(byte[] head) {
        return starts(head, BOX_OFFSET, BOX) && head.length >= HEAD
            ? Optional.of(new String(head, BRAND_OFFSET, BRAND_LENGTH, StandardCharsets.US_ASCII))
            : Optional.empty();
    }

    private static boolean starts(byte[] head, int offset, byte[] prefix) {
        return head.length >= offset + prefix.length
            && Arrays.equals(head, offset, offset + prefix.length, prefix, 0, prefix.length);
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private record Signature(boolean matches, Detected detected) {
    }
}
