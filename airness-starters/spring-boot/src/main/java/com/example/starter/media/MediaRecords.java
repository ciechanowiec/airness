package com.example.starter.media;

import com.example.starter.TransactionTimeout;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The rows that say what is stored: one per file and one per rendered set of widths. Each method is a
 * transaction of its own, or joins the caller's, because storing a file writes its bytes outside any
 * transaction and its row inside a short one.
 *
 * <p>The class is not final because the container wraps it in a proxy, which is a subclass.
 */
@Component
public class MediaRecords {

    private final MediaObjectRepository files;

    private final MediaRenditionRepository renditions;

    /**
     * Reads and writes the rows through the given repositories.
     *
     * @param files      the stored files
     * @param renditions the rendered sets of widths
     */
    public MediaRecords(MediaObjectRepository files, MediaRenditionRepository renditions) {
        this.files = files;
        this.renditions = renditions;
    }

    /**
     * Answers the file known by the given reference.
     *
     * @param reference the reference the file is known by outside the database
     * @return that file, or nothing where none is known by it
     */
    @Transactional(readOnly = true, timeout = TransactionTimeout.SECONDS)
    public Optional<MediaObject> file(UUID reference) {
        return this.files.findByReference(reference);
    }

    /**
     * Answers the renditions of the given bytes for the given purpose.
     *
     * @param key the bytes and the purpose
     * @return the renditions, or nothing where none were made
     */
    @Transactional(readOnly = true, timeout = TransactionTimeout.SECONDS)
    public Optional<MediaRendition> rendition(RenditionKey key) {
        return this.renditions.findById(key);
    }

    /**
     * Saves a stored file.
     *
     * @param file the file whose bytes are already stored
     * @return the file as saved
     */
    @Transactional(readOnly = false, timeout = TransactionTimeout.SECONDS)
    public MediaObject saved(MediaObject file) {
        return this.files.save(file);
    }

    /**
     * Saves a rendered set of widths, over one an equal upload saved meanwhile.
     *
     * @param rendition the renditions whose bytes are already stored
     */
    @Transactional(readOnly = false, timeout = TransactionTimeout.SECONDS)
    public void saved(MediaRendition rendition) {
        this.renditions.save(rendition);
    }

    /**
     * Answers how the files with the given identifiers are drawn, their renditions read in one query.
     *
     * @param ids the identifiers of the files
     * @return each file as a page shows it, under its identifier
     */
    @Transactional(readOnly = true, timeout = TransactionTimeout.SECONDS)
    public Map<Long, MediaView> views(Collection<Long> ids) {
        List<MediaObject> found = this.files.findByIdIn(ids, Limit.of(Math.max(1, ids.size())));
        Map<RenditionKey, MediaRendition> drawn = this.renditions.findAllById(
            found.stream().map(MediaRecords::keyOf).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(MediaRendition::getKey, Function.identity()));
        return found.stream().collect(
            Collectors.toMap(MediaObject::getId, file -> view(file, Optional.ofNullable(drawn.get(keyOf(file)))))
        );
    }

    /**
     * Removes the rows of the files with the given identifiers, and of every rendered set no file holds any
     * longer.
     *
     * @param ids the identifiers of the files
     * @return the files removed, and the rendered sets no file holds any longer
     */
    @Transactional(readOnly = false, timeout = TransactionTimeout.SECONDS)
    public Removal removed(Collection<Long> ids) {
        List<MediaObject> found = this.files.findByIdIn(ids, Limit.of(Math.max(1, ids.size())));
        this.files.deleteAll(found);
        this.files.flush();
        List<RenditionKey> orphaned = found.stream()
            .map(MediaRecords::keyOf)
            .distinct()
            .filter(key -> !this.files.existsByFileSha256AndPurpose(key.sha256(), key.purpose()))
            .toList();
        this.renditions.deleteAllById(orphaned);
        return new Removal(found.stream().map(MediaObject::getReference).toList(), orphaned);
    }

    static RenditionKey keyOf(MediaObject file) {
        return new RenditionKey(file.getFile().sha256(), file.getPurpose());
    }

    static MediaView view(MediaObject file, Optional<MediaRendition> rendition) {
        Rendering rendering = rendition.map(drawn -> new Rendering(drawn.getWidth(), drawn.getHeight(), drawn.widths()))
            .orElse(Rendering.NONE);
        return new MediaView(file.getReference(), file.getFile().originalName(), file.getFile().kind(), rendering);
    }

    /**
     * What a removal took away.
     *
     * @param files      the references of the files removed
     * @param renditions the rendered sets no file holds any longer
     */
    public record Removal(List<UUID> files, List<RenditionKey> renditions) {

        /**
         * Keeps copies of what was removed, so the removal stays what it was.
         */
        public Removal {
            files = List.copyOf(files);
            renditions = List.copyOf(renditions);
        }
    }
}
