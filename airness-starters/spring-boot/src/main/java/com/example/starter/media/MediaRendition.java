package com.example.starter.media;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * The widths the bytes of a picture were rendered at for one purpose, and the size of the widest rendition,
 * which is the size every rendition keeps the proportions of. The widths are kept as one comma-separated
 * column, since they are only ever read together.
 *
 * <p>The class is not final and neither are its fields, because Hibernate subclasses a persistent type and
 * writes its fields after the constructor has run. Nothing in this codebase writes one.
 */
@Entity(name = "MediaRendition")
@Access(AccessType.FIELD)
@Table(name = "media_rendition")
@Getter
public class MediaRendition {

    private static final String SEPARATOR = ",";

    @EmbeddedId
    private RenditionKey key;

    @Column(name = "width", nullable = false, updatable = false)
    private int width;

    @Column(name = "height", nullable = false, updatable = false)
    private int height;

    @Getter(AccessLevel.NONE)
    @Column(name = "widths", nullable = false, updatable = false)
    private String widths;

    /**
     * Leaves the renditions for the persistence provider to fill in from a row it has read.
     */
    protected MediaRendition() {
        this.key = new RenditionKey("", MediaPurpose.ATTACHMENT);
        this.widths = "";
    }

    /**
     * Records the renditions of the given bytes for the given purpose.
     *
     * @param key    whose renditions these are
     * @param width  the width of the widest rendition
     * @param height the height of the widest rendition
     * @param widths every width a rendition was written at, narrowest first
     */
    public MediaRendition(RenditionKey key, int width, int height, List<Integer> widths) {
        this.key = key;
        this.width = width;
        this.height = height;
        this.widths = widths.stream().map(String::valueOf).collect(Collectors.joining(SEPARATOR));
    }

    /**
     * Answers every width a rendition was written at.
     *
     * @return the widths, narrowest first
     */
    public List<Integer> widths() {
        return Arrays.stream(this.widths.split(SEPARATOR)).map(Integer::valueOf).toList();
    }
}
