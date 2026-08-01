package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Writes the files the harness owns into a project, which is the only thing here that writes anything.
 *
 * <p>It is deliberately not what a verifying build runs. A build that repaired the tree on its way past
 * would make a green build a statement about a tree the build had reshaped rather than about the tree
 * that was committed, and the difference is invisible afterwards. So checking and repairing are two
 * goals, and only one of them is bound to a phase.
 *
 * <p>Nothing is ever deleted. A file the harness forbids is reported by the check with the reason and
 * the remedy, and removing it is left to whoever put it there: a build tool deleting a developer's file
 * on their behalf is a worse failure than the one it would be fixing.
 */
public final class AssetSync {

    private final Path root;
    private final AssetCatalogue catalogue;
    private final Set<String> unmanaged;

    /**
     * Reads nothing yet, holding only where to write and what to leave alone.
     *
     * @param root      the working tree root
     * @param catalogue the files the harness owns
     * @param unmanaged repository-relative paths this project has taken over
     */
    public AssetSync(Path root, AssetCatalogue catalogue, Collection<String> unmanaged) {
        this.root = root;
        this.catalogue = catalogue;
        this.unmanaged = Set.copyOf(unmanaged);
    }

    /**
     * Writes what is missing or has drifted, and reports what it wrote.
     *
     * <p>A pinned file is written whenever it differs. A seed is written only when it is absent, since
     * its body belongs to the project the moment it exists.
     *
     * @return the paths written, in manifest order
     */
    public List<String> write() {
        return this.catalogue.assets().stream()
            .filter(asset -> !this.unmanaged.contains(asset.path()))
            .filter(this::needsWriting)
            .map(this::materialize)
            .toList();
    }

    private boolean needsWriting(ManagedAsset asset) {
        return switch (asset.policy()) {
            case PINNED -> !new AssetCheck(this.root, this.catalogue, this.unmanaged).matches(asset);
            case SEED -> !Files.exists(this.root.resolve(asset.path()));
            case FORBIDDEN -> false;
        };
    }

    private String materialize(ManagedAsset asset) {
        byte[] content = this.catalogue.canonical(asset.path()).orElseThrow(
            () -> new IllegalStateException("The harness ships no bytes for " + asset.path())
        );
        write(this.root.resolve(asset.path()), content);
        return asset.path();
    }

    private static void write(Path file, byte[] content) {
        try {
            Optional.ofNullable(file.getParent()).ifPresent(AssetSync::directory);
            Files.write(file, content);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write " + file, exception);
        }
    }

    private static void directory(Path parent) {
        try {
            Files.createDirectories(parent);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not create " + parent, exception);
        }
    }
}
