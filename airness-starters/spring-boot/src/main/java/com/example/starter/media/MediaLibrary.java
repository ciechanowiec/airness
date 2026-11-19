package com.example.starter.media;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Stores the files people hand the application and has their pictures rendered once, when they are stored.
 *
 * <p>Storing a file reads it once into a temporary file, which names its bytes by their SHA-256 and tells what
 * they are from their first bytes. The file is written to the object store under a reference of its own, and
 * its picture, or the first page of a PDF, is rendered at every width its purpose draws it at. Renditions are
 * named by the bytes rather than by the file, so the same bytes stored twice are rendered once. The row naming
 * the file is saved last, so a file is never named before its bytes are stored, and whatever a failed store
 * wrote is removed again.
 *
 * <p>Nothing here runs inside a transaction of its own, because rendering takes longer than any transaction of
 * the application is allowed to. Each row is saved in a short one.
 */
@Service
public final class MediaLibrary {

    /**
     * The most bytes a file may hold.
     */
    public static final long MAX_BYTES = 25L * 1024 * 1024;

    private static final String MEDIA = "media/";

    private final MediaStorage storage;

    private final Renderer renderer;

    private final MediaRecords records;

    private final Clock clock;

    /**
     * Stores files through the given store, renderer and rows.
     *
     * @param storage  the object store the bytes are written into
     * @param renderer what renders the pictures of stored files
     * @param records  the rows that say what is stored
     * @param clock    the clock the moment a file is stored is read from
     */
    MediaLibrary(MediaStorage storage, Renderer renderer, MediaRecords records, Clock clock) {
        this.storage = storage;
        this.renderer = renderer;
        this.records = records;
        this.clock = clock;
    }

    /**
     * Stores the given file for the given purpose.
     *
     * @param upload  the file
     * @param actor   the identifier of the account storing it
     * @param purpose what the file is for
     * @return the file as saved
     * @throws MediaRefusedException when the file is empty, too large or none of the kinds the application draws
     * @throws UncheckedIOException  when the file cannot be read, stored or rendered
     */
    public MediaObject store(Upload upload, long actor, MediaPurpose purpose) {
        try (SpooledUpload spooled = SpooledUpload.of(upload)) {
            MediaObject media = new MediaObject(
                spooled.described(upload.name()), purpose, actor, this.clock.instant()
            );
            this.written(media, spooled);
            return this.records.saved(media);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * Answers the file known by the given reference.
     *
     * @param reference the reference the file is known by outside the database
     * @return that file, or nothing where none is known by it
     */
    public Optional<MediaObject> file(UUID reference) {
        return this.records.file(reference);
    }

    /**
     * Answers how the files with the given identifiers are drawn.
     *
     * @param ids the identifiers of the files
     * @return each file as a page shows it, under its identifier
     */
    public Map<Long, MediaView> views(Collection<Long> ids) {
        return ids.isEmpty() ? Map.of() : this.records.views(ids);
    }

    /**
     * Opens the bytes the given file was handed over as.
     *
     * @param file the file
     * @return its bytes, which the caller closes
     */
    public StoredObject original(MediaObject file) {
        return this.storage.open(originalKey(file.getReference()));
    }

    /**
     * Opens one rendition of the given file.
     *
     * @param file   the file
     * @param width  the width of the rendition
     * @param format its encoding
     * @return its bytes, or nothing where the file was not rendered at that width
     */
    public Optional<StoredObject> rendition(MediaObject file, int width, ImageFormat format) {
        RenditionKey key = MediaRecords.keyOf(file);
        return this.records.rendition(key)
            .filter(rendition -> rendition.widths().contains(width))
            .map(_ -> this.storage.open(key.key(width, format)));
    }

    /**
     * Removes the files with the given identifiers: their rows first, in a transaction of their own, and their
     * bytes once those rows are gone, so no row ever names bytes that were removed. It is called outside any
     * transaction, once whatever held the files has let them go.
     *
     * @param ids the identifiers of the files
     */
    public void discard(Collection<Long> ids) {
        if (!ids.isEmpty()) {
            MediaRecords.Removal removal = this.records.removed(ids);
            removal.files().forEach(reference -> this.storage.removeUnder(MEDIA + reference + "/"));
            removal.renditions().forEach(key -> this.storage.removeUnder(key.prefix()));
        }
    }

    private void written(MediaObject media, SpooledUpload spooled) throws IOException {
        String original = originalKey(media.getReference());
        this.storage.put(original, spooled.file(), media.getFile().contentType());
        boolean written = false;
        try {
            Optional<String> source = media.getFile().kind() == MediaKind.PDF
                ? this.page(media, spooled)
                : Optional.of(original);
            if (source.isPresent()) {
                this.renderer.render(source.get(), MediaRecords.keyOf(media));
            }
            written = true;
        } finally {
            if (!written) {
                this.storage.removeUnder(MEDIA + media.getReference() + "/");
            }
        }
    }

    private Optional<String> page(MediaObject media, SpooledUpload spooled) {
        Optional<byte[]> page = spooled.firstPage();
        String key = MEDIA + media.getReference() + "/page.png";
        page.ifPresent(png -> this.storage.put(key, png, "image/png"));
        return page.map(_ -> key);
    }

    private static String originalKey(UUID reference) {
        return MEDIA + reference + "/original";
    }
}
