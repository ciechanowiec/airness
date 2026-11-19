package com.example.starter.media;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.Caller;
import com.example.starter.DatabaseTest;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@DatabaseTest
class MediaLibraryTest {

    private static final int PHOTO_WIDTH = 1000;

    private static final List<Integer> PHOTO_WIDTHS = List.of(320, 640, 960, 1000);

    private static final List<Integer> PORTRAIT_WIDTHS = List.of(64, 128, 256);

    private static final int PHOTO_HEIGHT = 750;

    private static final int PORTRAIT_SIDE = 400;

    private static final int WIDEST_PORTRAIT = 256;

    private static final int PAGE_WIDTH = 1600;

    private static final int NARROWEST = 320;

    private static final int MIDDLE = 640;

    private static final int WIDER_THAN_PHOTO = 1280;

    private static final int TINY = 8;

    // A size no other test stores, so the bytes of this picture are shared by the files of one test alone.
    private static final int SHARED_WIDTH = 820;

    private static final int SHARED_HEIGHT = 615;

    // The first bytes of a PNG and part of its header, with the rest of the file missing.
    private static final int CUT_AFTER = 24;

    private static final int OVERLONG = 400;

    // A page one point wide and far taller than any sheet, which fitted to the page width alone would decode into
    // a picture of millions of pixels.
    private static final float STRIP_WIDTH = 1;

    private static final float STRIP_HEIGHT = 2000;

    private static final int PAGE_HEIGHT_BOUND = 4000;

    private final MediaLibrary library;

    private final MediaStorage storage;

    private final long actor;

    // Files are stored as the first administrator, whom every run writes.
    MediaLibraryTest(MediaLibrary library, MediaStorage storage, JdbcTemplate database) {
        this.library = library;
        this.storage = storage;
        this.actor = Objects.requireNonNull(
            database.queryForObject("SELECT id FROM app_user WHERE email = ?", Long.class, Caller.ADMINISTRATOR)
        );
    }

    private static Upload upload(String name, byte[] bytes) {
        return new Upload(name, new ByteArrayResource(bytes));
    }

    private MediaView view(MediaObject file) {
        return Objects.requireNonNull(this.library.views(List.of(file.getId())).get(file.getId()), "the file is drawn");
    }

    private MediaObject stored(String name, byte[] bytes) {
        return this.library.store(upload(name, bytes), this.actor, MediaPurpose.ATTACHMENT);
    }

    @Test
    void rendersAPictureAtEveryWidthUpToItsOwnInBothEncodings() {
        MediaObject file = this.stored("photo.jpg", Pictures.jpeg(PHOTO_WIDTH, PHOTO_HEIGHT));
        assertAll(
            () -> assertEquals(file, this.library.file(file.getReference()).orElseThrow()),
            () -> assertEquals(MediaKind.IMAGE, file.getFile().kind()),
            () -> assertEquals("image/jpeg", file.getFile().contentType()),
            () -> assertEquals(
                new Rendering(PHOTO_WIDTH, PHOTO_HEIGHT, PHOTO_WIDTHS),
                this.view(file).rendering()
            ),
            () -> assertTrue(this.library.rendition(file, PHOTO_WIDTH, ImageFormat.AVIF).isPresent()),
            () -> assertTrue(this.library.rendition(file, NARROWEST, ImageFormat.WEBP).isPresent()),
            () -> assertFalse(this.library.rendition(file, WIDER_THAN_PHOTO, ImageFormat.WEBP).isPresent())
        );
    }

    @Test
    void drawsAPortraitOnlyAtTheWidthsAPortraitIsDrawnAt() {
        MediaObject file = this.library.store(
            upload("me.png", Pictures.png(PORTRAIT_SIDE, PORTRAIT_SIDE)), this.actor, MediaPurpose.PORTRAIT
        );
        assertEquals(
            new Rendering(WIDEST_PORTRAIT, WIDEST_PORTRAIT, PORTRAIT_WIDTHS), this.view(file).rendering()
        );
    }

    @Test
    void drawsAPdfByItsFirstPage() {
        MediaView view = this.view(this.stored("brief.pdf", Pictures.pdf()));
        assertAll(
            () -> assertEquals(MediaKind.PDF, view.kind()),
            () -> assertTrue(view.rendering().drawable()),
            () -> assertEquals(PAGE_WIDTH, view.rendering().width())
        );
    }

    @Test
    void keepsAPdfThatCannotBeReadWithoutAPicture() {
        MediaObject file = this.stored("broken.pdf", "%PDF-1.7 nothing follows".getBytes(StandardCharsets.US_ASCII));
        assertEquals(Rendering.NONE, this.view(file).rendering());
    }

    @Test
    void drawsAPageOfAnyProportionsNoLargerThanItsBounds() {
        Rendering rendering = this.view(this.stored("strip.pdf", Pictures.pdf(STRIP_WIDTH, STRIP_HEIGHT))).rendering();
        assertTrue(rendering.height() <= PAGE_HEIGHT_BOUND);
    }

    @Test
    void keepsAPdfWhosePageHasNoAreaWithoutAPicture() {
        assertEquals(Rendering.NONE, this.view(this.stored("flat.pdf", Pictures.pdf(STRIP_WIDTH, 0))).rendering());
    }

    @Test
    void keepsAPdfWithNoPageWithoutAPicture() {
        assertEquals(Rendering.NONE, this.view(this.stored("blank.pdf", Pictures.pagelessPdf())).rendering());
    }

    @Test
    void keepsNothingOfAPictureTheRendererCannotRead() {
        byte[] damaged = Arrays.copyOf(Pictures.png(TINY, TINY), CUT_AFTER);
        assertThrows(UncheckedIOException.class, () -> this.stored("damaged.png", damaged));
    }

    @Test
    void discardsNothingWhereNoFileIsNamed() {
        assertDoesNotThrow(() -> this.library.discard(List.of()));
    }

    @Test
    @SneakyThrows
    void drawsAPhotographTakenOnAPhone() {
        byte[] heic = new ClassPathResource("media/sample.heic").getContentAsByteArray();
        MediaObject file = this.stored("IMG_0001.HEIC", heic);
        assertAll(
            () -> assertEquals("image/heic", file.getFile().contentType()),
            () -> assertTrue(this.view(file).rendering().drawable())
        );
    }

    @Test
    void refusesAFileThatIsEmptyTooLargeOrNoPicture() {
        byte[] large = Arrays.copyOf(Pictures.jpeg(TINY, TINY), Math.toIntExact(MediaLibrary.MAX_BYTES + 1));
        byte[] text = "plain text".getBytes(StandardCharsets.US_ASCII);
        assertAll(
            () -> assertEquals(MediaRefusedException.Reason.EMPTY, this.refusal("empty.jpg", new byte[0])),
            () -> assertEquals(MediaRefusedException.Reason.TOO_LARGE, this.refusal("large.jpg", large)),
            () -> assertEquals(MediaRefusedException.Reason.UNSUPPORTED, this.refusal("notes.jpg", text))
        );
    }

    @Test
    void keepsTheRenditionsOfBytesUntilTheLastFileHoldingThemGoes() {
        byte[] bytes = Pictures.jpeg(SHARED_WIDTH, SHARED_HEIGHT);
        MediaObject first = this.stored("first.jpg", bytes);
        MediaObject second = this.stored("second.jpg", bytes);
        this.library.discard(List.of(first.getId()));
        assertAll(
            () -> assertTrue(this.library.file(first.getReference()).isEmpty()),
            () -> assertThrows(NoSuchKeyException.class, () -> this.library.original(first)),
            () -> assertTrue(this.library.rendition(second, MIDDLE, ImageFormat.AVIF).isPresent())
        );
        this.library.discard(List.of(second.getId()));
        String rendition = MediaRecords.keyOf(second).key(MIDDLE, ImageFormat.AVIF);
        assertAll(
            () -> assertTrue(this.library.rendition(second, MIDDLE, ImageFormat.AVIF).isEmpty()),
            () -> assertThrows(NoSuchKeyException.class, () -> this.storage.open(rendition))
        );
    }

    @Test
    void namesAFileHandedOverWithoutANameAndShortensAnOverlongOne() {
        byte[] dot = Pictures.png(TINY, TINY);
        assertAll(
            () -> assertEquals("file", this.stored("  ", dot).getFile().originalName()),
            () -> assertEquals(
                MediaFile.NAME, this.stored("x".repeat(OVERLONG) + ".png", dot).getFile().originalName().length()
            )
        );
    }

    private MediaRefusedException.Reason refusal(String name, byte[] bytes) {
        return assertThrows(MediaRefusedException.class, () -> this.stored(name, bytes)).getReason();
    }
}
