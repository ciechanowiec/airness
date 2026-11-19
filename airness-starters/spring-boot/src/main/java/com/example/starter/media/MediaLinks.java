package com.example.starter.media;

import java.util.Comparator;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The addresses a page draws a stored file from, which the media fragment asks for by the name
 * {@code media}: a source set of every rendition in one encoding, the one rendition a browser without source
 * sets draws, and the file as it was handed over.
 */
@Component("media")
public final class MediaLinks {

    /**
     * The width the rendition a browser without source sets draws is closest to.
     */
    private static final int FALLBACK = 640;

    /**
     * Answers the source set of a file's renditions in the given encoding, such as
     * {@code /media/{reference}/w320.avif 320w, /media/{reference}/w640.avif 640w}.
     *
     * @param view   the file as a page shows it
     * @param format the encoding, by extension
     * @return the source set
     */
    public String srcset(MediaView view, String format) {
        return this.srcset(view, format, Integer.MAX_VALUE);
    }

    /**
     * Answers the source set of a file's renditions in the given encoding no wider than the given width.
     *
     * @param view   the file as a page shows it
     * @param format the encoding, by extension
     * @param widest the widest rendition the source set may name, in pixels
     * @return the source set
     */
    public String srcset(MediaView view, String format, int widest) {
        ImageFormat encoding = ImageFormat.of(format);
        return view.rendering()
            .widths()
            .stream()
            .filter(width -> width <= widest)
            .map(width -> "%s %dw".formatted(rendition(view, width, encoding), width))
            .collect(Collectors.joining(", "));
    }

    /**
     * Answers the WebP rendition a browser that reads no source set draws: the narrowest at least 640 pixels
     * wide, or the widest where none is.
     *
     * @param view the file as a page shows it
     * @return the address of that rendition
     */
    public String fallback(MediaView view) {
        int width = view.rendering()
            .widths()
            .stream()
            .filter(candidate -> candidate >= FALLBACK)
            .findFirst()
            .orElseGet(() -> view.rendering().widths().stream().max(Comparator.naturalOrder()).orElse(FALLBACK));
        return rendition(view, width, ImageFormat.WEBP);
    }

    /**
     * Answers the address the file is downloaded from as it was handed over.
     *
     * @param view the file as a page shows it
     * @return that address
     */
    public String original(MediaView view) {
        return "%s/%s/original".formatted(MediaPages.MEDIA, view.reference());
    }

    private static String rendition(MediaView view, int width, ImageFormat format) {
        return "%s/%s/w%d.%s".formatted(MediaPages.MEDIA, view.reference(), width, format.extension());
    }
}
