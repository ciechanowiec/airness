package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Every image the repository carries decodes: none is truncated, corrupt or empty.
 *
 * <p>The set is the committable tree, with each image chosen by the extension of its name, so a broken
 * copy is reported wherever it sits. A tree with no image passes over an empty set, and the first image
 * added is checked from then on.
 */
public final class ImageAssetCheck {

    private static final String BROKEN
        = "Image files that do not decode, which a browser shows as broken (replace each with an intact copy)";

    private final Path root;
    private final List<Path> images;

    /**
     * Names the working tree whose images are read.
     *
     * @param root the working tree root
     */
    public ImageAssetCheck(Path root) {
        this.root = root;
        this.images = Repository.trackedFiles(root).stream()
            .filter(ImageBytes::supported)
            .filter(Files::isRegularFile)
            .toList();
    }

    /**
     * How many images the check reads.
     *
     * @return the image count
     */
    public int scanned() {
        return this.images.size();
    }

    /**
     * The images that do not decode, each with the reason.
     *
     * @return one verdict
     */
    public List<Findings> findings() {
        List<String> broken = this.images.stream()
            .flatMap(
                image -> ImageBytes.problem(image, read(image)).stream()
                    .map(reason -> this.root.relativize(image) + ": " + reason)
            ).toList();
        return List.of(new Findings(BROKEN, broken));
    }

    private static byte[] read(Path image) {
        try {
            return Files.readAllBytes(image);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + image, exception);
        }
    }
}
