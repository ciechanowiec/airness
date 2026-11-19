package com.example.starter.media;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Renders a stored picture at every width its purpose draws it at, in AVIF and in WebP, and writes each
 * rendition under the bytes it was made from. The widest WebP rendition is made first, which tells the size of
 * the picture and so which widths it is rendered at, and every other rendition is then asked for at once on the
 * application's task executor, since each is a request to the renderer, which works on several at a time.
 * Whatever a failed rendering wrote is removed again.
 */
@Component
final class Renderer {

    private final MediaStorage storage;

    private final ImageProcessor images;

    private final MediaRecords records;

    private final AsyncTaskExecutor executor;

    /**
     * Renders through the given renderer into the given store.
     *
     * @param storage  the object store the renditions are written into
     * @param images   the renderer the renditions are made by
     * @param records  the rows that say which renditions exist
     * @param executor the application's task executor, on which the renditions are asked for
     */
    Renderer(MediaStorage storage, ImageProcessor images, MediaRecords records, AsyncTaskExecutor executor) {
        this.storage = storage;
        this.images = images;
        this.records = records;
        this.executor = executor;
    }

    /**
     * Renders the stored picture under the given key for the given bytes and purpose, unless those were
     * rendered before.
     *
     * @param source the key of the stored picture
     * @param key    the bytes and the purpose the renditions are made for
     * @throws IOException when the renderer cannot be reached or refuses the picture
     */
    void render(String source, RenditionKey key) throws IOException {
        if (this.records.rendition(key).isPresent()) {
            return;
        }
        boolean rendered = false;
        try {
            Rendition widest = this.images.render(source, key.purpose().widest(), ImageFormat.WEBP);
            this.storage.put(key.key(widest.width(), ImageFormat.WEBP), widest.bytes(), ImageFormat.WEBP.contentType());
            List<Integer> widths = key.purpose().widthsUpTo(widest.width());
            this.renderedAll(source, key, widths, widest.width());
            this.records.saved(new MediaRendition(key, widest.width(), widest.height(), widths));
            rendered = true;
        } finally {
            if (!rendered) {
                this.storage.removeUnder(key.prefix());
            }
        }
    }

    private void renderedAll(String source, RenditionKey key, List<Integer> widths, int widest) throws IOException {
        List<Future<?>> pending = widths.stream()
            .flatMap(width -> Stream.of(ImageFormat.values()).map(format -> new Wanted(width, format)))
            .filter(wanted -> wanted.width() != widest || wanted.format() != ImageFormat.WEBP)
            .<Future<?>>map(wanted -> this.executor.submit(() -> this.renderedOne(source, key, wanted)))
            .toList();
        for (Future<?> request : pending) {
            awaited(request);
        }
    }

    private void renderedOne(String source, RenditionKey key, Wanted wanted) {
        try {
            Rendition rendition = this.images.render(source, wanted.width(), wanted.format());
            this.storage.put(
                key.key(wanted.width(), wanted.format()), rendition.bytes(), wanted.format().contentType()
            );
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void awaited(Future<?> request) throws IOException {
        try {
            request.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while rendering", exception);
        } catch (ExecutionException exception) {
            throw new IOException("A rendition could not be made", exception);
        }
    }

    private record Wanted(int width, ImageFormat format) {
    }
}
