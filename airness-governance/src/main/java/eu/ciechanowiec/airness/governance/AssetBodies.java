package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * The bytes the assets artifact ships, read off a classloader.
 *
 * <p>A layer ships the body of a root file under {@code airness/files/<layer>/} and the body of a file placed
 * in each node project under {@code airness/project-files/<layer>/}, so one path can read differently at the
 * root and inside a node project.
 *
 * <p>A body is stored under an added {@code .asset} suffix. Without it, the copy of {@code .gitignore} this
 * jar ships would be a working {@code .gitignore} over its own source directory and could stop the other
 * canonical files from being tracked, and the copy of {@code .gitattributes} would take effect over them. A
 * file that changes the meaning of the directory it is stored in cannot be stored under its own name.
 *
 * <p>The harness's own working tree is held to additions no governed project receives: a pinned root file
 * for which {@code airness/self-files/<layer>/} ships a body is followed there by that body, which is how
 * each clone of the harness names a local Maven repository of its own. {@link #selfTree()} gives that
 * reading. An addition follows the shipped bytes and replaces none of them, so every other deviation still
 * drifts, and a governed project that carries an addition drifts as well.
 */
final class AssetBodies {

    private static final String FILES = "airness/files/";
    private static final String PROJECT_FILES = "airness/project-files/";
    private static final String SELF_FILES = "airness/self-files/";
    private static final String SUFFIX = ".asset";

    private final ClassLoader classes;
    private final boolean self;

    /**
     * Reads the bytes a governed project is held to.
     *
     * @param classes the classloader carrying the assets artifact
     */
    AssetBodies(ClassLoader classes) {
        this(classes, false);
    }

    private AssetBodies(ClassLoader classes, boolean self) {
        this.classes = classes;
        this.self = self;
    }

    /**
     * The same bytes as the harness's own working tree is held to them.
     *
     * @return the bodies of the harness's own tree
     */
    AssetBodies selfTree() {
        return new AssetBodies(this.classes, true);
    }

    /**
     * The bytes the harness ships for a managed file: a whole file, or the body of one block, followed in the
     * harness's own tree by the self addition of a pinned root file.
     *
     * @param asset the managed file
     * @return the canonical bytes, or nothing when the harness ships none for it
     */
    Optional<byte[]> canonical(ManagedAsset asset) {
        Optional<byte[]> addition = this.self && addable(asset) ? this.read(selfResource(asset)) : Optional.empty();
        return this.read(resource(asset))
            .map(shipped -> addition.map(extra -> joined(shipped, extra)).orElse(shipped));
    }

    /**
     * Reads one resource of the assets artifact.
     *
     * @param resource the resource name
     * @return its bytes, or nothing when the artifact does not carry it
     */
    Optional<byte[]> read(String resource) {
        try (InputStream stream = this.classes.getResourceAsStream(resource)) {
            return Optional.ofNullable(stream).map(AssetBodies::drain);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + resource, exception);
        }
    }

    /**
     * Where the body of a managed file is shipped.
     *
     * @param asset the managed file
     * @return the resource name of its body
     */
    static String resource(ManagedAsset asset) {
        String base = asset.location() == AssetLocation.ROOT ? FILES : PROJECT_FILES;
        // Scaffolds can target development-control directories. Their flat, encoded resource names keep
        // those directories out of the production archive while preserving the target path in the manifest.
        String path = asset.policy() == AssetPolicy.SCAFFOLD
            ? URLEncoder.encode(asset.path(), StandardCharsets.UTF_8) : asset.path();
        return base + asset.owner().id() + '/' + path + SUFFIX;
    }

    /**
     * Where the self addition of a managed file is shipped, whether or not one is.
     *
     * @param asset the managed file
     * @return the resource name of its self addition
     */
    static String selfResource(ManagedAsset asset) {
        return SELF_FILES + asset.owner().id() + '/' + asset.path() + SUFFIX;
    }

    private static boolean addable(ManagedAsset asset) {
        return asset.policy() == AssetPolicy.PINNED && asset.location() == AssetLocation.ROOT;
    }

    private static byte[] joined(byte[] shipped, byte[] addition) {
        return ByteBuffer.allocate(shipped.length + addition.length).put(shipped).put(addition).array();
    }

    private static byte[] drain(InputStream stream) {
        try {
            return stream.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read a shipped asset", exception);
        }
    }
}
