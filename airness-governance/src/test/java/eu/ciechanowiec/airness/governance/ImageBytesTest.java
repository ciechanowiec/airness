package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Verifies each image format is judged by the structure a damaged copy loses.
 */
class ImageBytesTest {

    private static final String PNG = "a.png";
    private static final String JPEG = "a.jpg";
    // The end chunk is twelve bytes: a length, a type and a checksum around an empty payload.
    private static final int END_CHUNK = 12;
    // Cutting the end chunk and eight more bytes leaves the image data chunk running past the file.
    private static final int INTO_IMAGE_DATA = END_CHUNK + 8;
    private static final int RAW_IMAGE = 64;
    private static final int HUFFMAN_TABLE = 0xC4;
    private static final int EXIF = 0xE1;
    private static final String TRUNCATED_JPEG = "truncated JPEG (missing EOI marker)";

    private static Optional<String> problem(String name, byte[] bytes) {
        return ImageBytes.problem(Path.of(name), bytes);
    }

    @Test
    void acceptsAnIntactImageOfEveryFormat() {
        assertEquals(Optional.empty(), problem(PNG, ImageFixtures.png()), "an intact PNG decodes");
        assertEquals(Optional.empty(), problem("a.JPG", ImageFixtures.jpeg()), "the extension is read in any case");
        assertEquals(Optional.empty(), problem("a.jpeg", ImageFixtures.jpeg()), "the long JPEG name is read too");
        assertEquals(Optional.empty(), problem("a.gif", ImageFixtures.gif()), "an intact GIF decodes");
        assertEquals(Optional.empty(), problem("a.webp", ImageFixtures.webp()), "an intact WebP decodes");
        assertEquals(Optional.empty(), problem("a.ico", ImageFixtures.ico()), "an intact icon decodes");
    }

    @Test
    void namesTheDefectOfADamagedPng() {
        byte[] png = ImageFixtures.png();
        assertEquals(
            Optional.of("not a valid PNG (bad signature)"), problem(PNG, ImageFixtures.withByte(png, 1, 'X')),
            "a damaged signature is not a PNG"
        );
        assertEquals(
            Optional.of("truncated PNG (chunk \"IDAT\" runs past end of file)"),
            problem(PNG, ImageFixtures.withoutLast(png, INTO_IMAGE_DATA)),
            "a chunk that runs past the file is a truncated upload"
        );
        assertEquals(
            Optional.of("invalid PNG (no IDAT image data)"), problem(PNG, ImageFixtures.pngWithoutImageData()),
            "a PNG with no image data draws nothing"
        );
        assertEquals(
            Optional.of("corrupt PNG (image data failed to inflate)"),
            problem(PNG, ImageFixtures.png(new byte[] {0x01, 0x02, 0x03})),
            "image data that does not inflate is corrupt"
        );
    }

    @Test
    void reportsAPngWhoseChunkStreamStopsBeforeItsEnd() {
        byte[] png = ImageFixtures.png();
        assertEquals(
            Optional.of("truncated PNG (no IEND chunk)"), problem(PNG, ImageFixtures.withoutLast(png, END_CHUNK)),
            "a stream cut exactly at a chunk boundary has lost its end marker"
        );
    }

    @Test
    void reportsATruncatedPngImageStream() {
        byte[] compressed = ImageFixtures.deflated(new byte[RAW_IMAGE]);
        byte[] cut = ImageFixtures.png(ImageFixtures.withoutLast(compressed, 4));
        assertEquals(
            Optional.of("corrupt PNG (image data failed to inflate)"), problem(PNG, cut),
            "image data that ends before its stream does is corrupt"
        );
    }

    @Test
    void namesTheDefectOfEachOtherDamagedFormat() {
        assertEquals(
            Optional.of("truncated JPEG (missing EOI marker)"),
            problem(JPEG, ImageFixtures.withoutLast(ImageFixtures.jpeg(), 1)), "a JPEG cut short has no end marker"
        );
        assertEquals(
            Optional.of("not a valid JPEG (bad SOI marker)"),
            problem(JPEG, ImageFixtures.withByte(ImageFixtures.jpeg(), 1, 0)), "a JPEG must open with its marker"
        );
        assertEquals(
            Optional.of("truncated GIF (missing trailer byte)"),
            problem("a.gif", ImageFixtures.withoutLast(ImageFixtures.gif(), 1)), "a GIF cut short has no trailer"
        );
        assertEquals(
            Optional.of("not a valid GIF (bad header)"),
            problem("a.gif", ImageFixtures.withByte(ImageFixtures.gif(), 4, 'x')),
            "a GIF must name its version"
        );
        assertEquals(
            Optional.of("truncated WebP (RIFF size exceeds file length)"),
            problem("a.webp", ImageFixtures.withoutLast(ImageFixtures.webp(), 1)), "a WebP shorter than it declares"
        );
        assertEquals(
            Optional.of("not a valid WebP (bad RIFF/WEBP header)"),
            problem("a.webp", ImageFixtures.withByte(ImageFixtures.webp(), 8, 'X')), "a WebP must name its form"
        );
    }

    @Test
    void acceptsAJpegFollowedByTheTrailerAPhoneAppends() {
        // The octal escapes spell single bytes: 377 330 377 341 is FF D8 FF E1, and 030 is a box length of 24.
        byte[] samsung = "\0\0SEFHinfo\377\330\377\341SEFT".getBytes(StandardCharsets.ISO_8859_1);
        byte[] motionPhoto = "\0\0\0\030ftypmp42\0\0\0\0isommp42\377".getBytes(StandardCharsets.ISO_8859_1);
        for (byte[] trailer : List.of(samsung, motionPhoto)) {
            assertEquals(
                Optional.empty(), problem(JPEG, ImageFixtures.joined(ImageFixtures.jpeg(), trailer)),
                "what follows the end marker is not the image"
            );
        }
    }

    @Test
    void acceptsAProgressiveJpegWhoseScansAreSeparatedBySegments() {
        byte[] table = ImageFixtures.jpegSegment(HUFFMAN_TABLE, new byte[] {0x00, 0x01});
        byte[] progressive = ImageFixtures.joined(
            ImageFixtures.jpegStart(), table, ImageFixtures.jpegScan(), table, ImageFixtures.jpegScan(),
            ImageFixtures.jpegEnd()
        );
        assertEquals(Optional.empty(), problem(JPEG, progressive), "a later scan follows a table of its own");
        assertEquals(
            Optional.of(TRUNCATED_JPEG),
            problem(JPEG, ImageFixtures.withoutLast(progressive, ImageFixtures.jpegEnd().length)),
            "and the image still has to end"
        );
    }

    @Test
    void readsNoEndMarkerFromAThumbnailEmbeddedInTheMetadata() {
        byte[] thumbnail = ImageFixtures.joined(
            "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1), ImageFixtures.jpeg()
        );
        byte[] metadata = ImageFixtures.jpegSegment(EXIF, thumbnail);
        assertEquals(
            Optional.of(TRUNCATED_JPEG),
            problem(JPEG, ImageFixtures.joined(ImageFixtures.jpegStart(), metadata, ImageFixtures.jpegScan())),
            "the main image never reached its end marker"
        );
        assertEquals(
            Optional.of(TRUNCATED_JPEG), problem(JPEG, ImageFixtures.joined(ImageFixtures.jpegStart(), metadata)),
            "a file cut just after the thumbnail ends with the thumbnail's end marker and not its own"
        );
        byte[] cut = ImageFixtures.withoutLast(metadata, 1);
        assertEquals(
            Optional.of(TRUNCATED_JPEG),
            problem(JPEG, ImageFixtures.joined(ImageFixtures.jpegStart(), cut, ImageFixtures.jpegEnd())),
            "a segment declaring more than the file holds is cut short, whatever its last bytes are"
        );
    }

    @Test
    void namesTheDefectOfADamagedIcon() {
        assertEquals(
            Optional.of("truncated ICO (image data runs past end of file)"),
            problem("a.ico", ImageFixtures.withoutLast(ImageFixtures.ico(), 1)), "an icon shorter than its entry"
        );
        assertEquals(
            Optional.of("truncated ICO (directory runs past end of file)"),
            problem("a.ico", ImageFixtures.withoutLast(ImageFixtures.ico(), 8)), "an icon cut inside its directory"
        );
        assertEquals(
            Optional.of("invalid ICO (no image entries)"), problem("a.ico", ImageFixtures.icoWithoutEntries()),
            "an icon with no entry draws nothing"
        );
        assertEquals(
            Optional.of("not a valid ICO (bad header)"),
            problem("a.ico", ImageFixtures.withByte(ImageFixtures.ico(), 2, 2)),
            "a cursor is not an icon"
        );
    }

    @Test
    void reportsAnEmptyImageAndLeavesOtherFilesAlone() {
        assertEquals(Optional.of("empty file"), problem(PNG, new byte[0]), "an empty image draws nothing");
        assertEquals(Optional.empty(), problem("a.svg", new byte[0]), "a format this does not read is not judged");
        assertTrue(ImageBytes.supported(Path.of("icons/favicon.ico")), "an icon is an image this reads");
        assertFalse(ImageBytes.supported(Path.of("Makefile")), "a name with no extension is no image");
    }
}
