package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The files the harness owns and the bytes it ships for each, read off a classloader.
 *
 * <p>The manifest names a path and a policy, and nothing else. It deliberately carries no checksum: a
 * checksum in the manifest would be a second spelling of what the shipped bytes already say, and the two
 * would eventually disagree, at which point the manifest is believed and the bytes are not. Hashing the
 * shipped entry at the moment of comparison leaves one source of truth and nothing to regenerate.
 *
 * <p>A canonical file is stored under an added {@code .asset} suffix. Without it, the copy of
 * {@code .gitignore} this jar ships would be a working {@code .gitignore} over its own source directory
 * and could stop the other canonical files from being tracked, and the copy of {@code .gitattributes}
 * would take effect over them. A file that changes the meaning of the directory it is stored in cannot
 * be stored under its own name.
 */
public final class AssetCatalogue {

    private static final String MANIFEST = "airness/manifest.tsv";
    private static final String FILES = "airness/files/";
    private static final String SUFFIX = ".asset";
    private static final String COMMENT = "#";
    private static final String SEPARATOR = "\t";
    private static final int FIELDS = 2;

    private final ClassLoader classes;
    private final List<ManagedAsset> assets;

    /**
     * Reads the manifest off the given classloader.
     *
     * @param classes the classloader carrying the assets artifact
     */
    public AssetCatalogue(ClassLoader classes) {
        this.classes = classes;
        this.assets = parse(
            this.manifest().orElseThrow(
                () -> new IllegalStateException(
                    "No " + MANIFEST + " on the classpath, so nothing states which files the harness owns"
                )
            )
        );
    }

    /**
     * Every file the harness owns.
     *
     * @return the managed assets, in the order the manifest lists them
     */
    public List<ManagedAsset> assets() {
        return this.assets;
    }

    /**
     * The bytes the harness ships for a path.
     *
     * @param path the repository-relative path
     * @return the canonical bytes, or nothing when the harness ships none for it
     */
    Optional<byte[]> canonical(String path) {
        return this.read(FILES + path + SUFFIX);
    }

    private Optional<String> manifest() {
        return this.read(MANIFEST).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    private Optional<byte[]> read(String resource) {
        try (InputStream stream = this.classes.getResourceAsStream(resource)) {
            return Optional.ofNullable(stream).map(AssetCatalogue::drain);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + resource, exception);
        }
    }

    private static byte[] drain(InputStream stream) {
        try {
            return stream.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read a shipped asset", exception);
        }
    }

    private static List<ManagedAsset> parse(String manifest) {
        return manifest.lines()
            .map(String::strip)
            .filter(line -> !line.isEmpty() && !line.startsWith(COMMENT))
            .map(AssetCatalogue::entry)
            .toList();
    }

    private static ManagedAsset entry(String line) {
        String[] fields = line.split(SEPARATOR);
        if (fields.length != FIELDS) {
            throw new IllegalStateException("A manifest line needs a path and a policy: " + line);
        }
        return new ManagedAsset(fields[0].strip(), AssetPolicy.valueOf(fields[1].strip().toUpperCase(Locale.ROOT)));
    }
}
