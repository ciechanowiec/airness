package com.example.starter.media;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * Serves the stored files to whoever may see them: each rendition a page draws, and the whole of an attachment,
 * which is always downloaded rather than opened in the page. That is a PDF as it was handed over and a picture
 * as its clean copy, which keeps nothing of what the camera wrote into it. A portrait is served by its
 * renditions alone, since a page only ever draws it. A file the reader may not see is answered as one that does
 * not exist.
 *
 * <p>Neither a rendition nor a file ever changes under its address, since a changed file is a new file under
 * a new reference, so a browser keeps both for a year without asking again. They are kept by the browser
 * alone, because only a reader who may see them may have them.
 */
@Controller
@RequestMapping(MediaPages.MEDIA)
public final class MediaPages {

    /**
     * The path every stored file is served under.
     */
    public static final String MEDIA = "/media";

    private static final String REFERENCE = "reference";

    private static final CacheControl KEPT = CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable();

    private final MediaLibrary library;

    private final List<MediaAccess> access;

    /**
     * Serves the files of the given library to whoever the given rules let see them.
     *
     * @param library the stored files
     * @param access  who may see which file, one rule for each feature that stores files
     */
    public MediaPages(MediaLibrary library, List<MediaAccess> access) {
        this.library = library;
        this.access = List.copyOf(access);
    }

    /**
     * Answers one rendition of a file.
     *
     * @param reference the reference the file is known by
     * @param width     the width of the rendition
     * @param format    its encoding, by extension
     * @return the rendition
     */
    @GetMapping("/{reference}/w{width:\\d{1,4}}.{format:avif|webp}")
    public ResponseEntity<Resource> rendition(
        @PathVariable(name = REFERENCE, required = true) UUID reference,
        @PathVariable(name = "width", required = true) int width,
        @PathVariable(name = "format", required = true) String format
    ) {
        MediaObject file = this.seen(reference);
        ImageFormat encoding = ImageFormat.of(format);
        StoredObject rendition = this.library.rendition(file, width, encoding).orElseThrow(MediaPages::missing);
        return ResponseEntity.ok()
            .cacheControl(KEPT)
            .contentType(MediaType.parseMediaType(encoding.contentType()))
            .contentLength(rendition.length())
            .body(new InputStreamResource(rendition.content()));
    }

    /**
     * Answers the whole of an attachment, for the reader to download: a PDF as it was handed over and a picture as
     * its clean copy.
     *
     * @param reference the reference the file is known by
     * @return the file
     */
    @GetMapping("/{reference}/original")
    public ResponseEntity<Resource> original(@PathVariable(name = REFERENCE, required = true) UUID reference) {
        MediaObject file = Optional.of(this.seen(reference))
            .filter(seen -> seen.getPurpose() == MediaPurpose.ATTACHMENT)
            .orElseThrow(MediaPages::missing);
        StoredObject original = this.library.original(file);
        return ResponseEntity.ok()
            .cacheControl(KEPT)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                    .filename(file.getFile().originalName(), StandardCharsets.UTF_8)
                    .build()
                    .toString()
            ).contentType(MediaType.parseMediaType(file.getFile().contentType()))
            .contentLength(original.length())
            .body(new InputStreamResource(original.content()));
    }

    private MediaObject seen(UUID reference) {
        return this.library.file(reference)
            .filter(file -> this.access.stream().anyMatch(rule -> rule.permits(file)))
            .orElseThrow(MediaPages::missing);
    }

    private static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
