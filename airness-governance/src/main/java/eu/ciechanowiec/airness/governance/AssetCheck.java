package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Every file the harness owns is where its policy says it should be, with the bytes it should have
 * where the policy owns those bytes.
 *
 * <p>Five disagreements are reported separately, because each of them names a different repair. A
 * pinned file that drifted and a seeded file that is missing are both repairs to run, but only the
 * pinned file has canonical content to compare. An opt-out that no longer differs from canonical is a
 * line to delete, and without that half the opt-out list rots into a blanket exemption. An opt-out
 * naming a path the manifest does not hold is a typo, and a typo in an exemption list reads as an
 * exemption that works.
 *
 * <p>The reason for an opt-out is not checked and cannot be. It lives as a comment beside the property
 * and binds by whoever reads the pom, which is worth saying plainly rather than implying a check that
 * does not exist.
 */
public final class AssetCheck {

    private static final String DRIFTED = "Files the harness owns that this project changed or is missing";
    private static final String MISSING = "Seeded files that must exist but are missing";
    private static final String PRESENT = "Files that must not be in the tree, since the harness supplies them";
    private static final String SETTLED = "Opt-outs that no longer differ from canonical, so delete these";
    private static final String UNKNOWN = "Opt-outs naming a path the harness does not own, which is a typo";

    private final Path root;
    private final AssetCatalogue catalogue;
    private final Set<String> unmanaged;

    /**
     * Reads nothing yet, holding only where to look and what to leave alone.
     *
     * @param root      the working tree root
     * @param catalogue the files the harness owns
     * @param unmanaged repository-relative paths this project has taken over
     */
    public AssetCheck(Path root, AssetCatalogue catalogue, Collection<String> unmanaged) {
        this.root = root;
        this.catalogue = catalogue;
        this.unmanaged = Set.copyOf(unmanaged);
    }

    /**
     * The four ways a project and the files the harness owns can disagree.
     *
     * @return one verdict per rule
     */
    public List<Findings> findings() {
        return List.of(
            new Findings(DRIFTED, this.drifted()),
            new Findings(MISSING, this.missing()),
            new Findings(PRESENT, this.present()),
            new Findings(SETTLED, this.settled()),
            new Findings(UNKNOWN, this.unknown())
        );
    }

    /**
     * Whether the file at a managed path is byte-for-byte what the harness ships.
     *
     * @param asset the managed file
     * @return whether the project's copy matches
     */
    public boolean matches(ManagedAsset asset) {
        Optional<byte[]> shipped = this.catalogue.canonical(asset.path());
        Optional<byte[]> held = this.held(asset.path());
        return shipped.isPresent() && held.isPresent() && Arrays.equals(shipped.get(), held.get());
    }

    private List<String> drifted() {
        return this.managed(AssetPolicy.PINNED)
            .filter(asset -> !this.matches(asset))
            .map(asset -> asset.path() + " (run mvn airness:assets-sync to restore it)")
            .toList();
    }

    private List<String> missing() {
        return this.managed(AssetPolicy.SEED)
            .filter(asset -> !Files.isRegularFile(this.root.resolve(asset.path()), LinkOption.NOFOLLOW_LINKS))
            .map(asset -> asset.path() + " (run mvn airness:assets-sync to create it)")
            .toList();
    }

    private List<String> present() {
        return this.managed(AssetPolicy.FORBIDDEN)
            .filter(asset -> Files.exists(this.root.resolve(asset.path()), LinkOption.NOFOLLOW_LINKS))
            .map(asset -> asset.path() + " (delete it, the harness supplies this file already)")
            .toList();
    }

    private List<String> settled() {
        return this.catalogue.assets().stream()
            .filter(asset -> this.unmanaged.contains(asset.path()))
            .filter(asset -> asset.policy() == AssetPolicy.PINNED && this.matches(asset))
            .map(ManagedAsset::path)
            .toList();
    }

    private List<String> unknown() {
        Set<String> owned = this.catalogue.assets().stream()
            .map(ManagedAsset::path)
            .collect(Collectors.toUnmodifiableSet());
        return this.unmanaged.stream().filter(path -> !owned.contains(path)).sorted().toList();
    }

    private Stream<ManagedAsset> managed(AssetPolicy policy) {
        return this.catalogue.assets().stream()
            .filter(asset -> asset.policy() == policy)
            .filter(asset -> !this.unmanaged.contains(asset.path()));
    }

    private Optional<byte[]> held(String path) {
        Path file = this.root.resolve(path);
        return Optional.of(file)
            .filter(candidate -> Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS))
            .map(AssetCheck::bytes);
    }

    private static byte[] bytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + file, exception);
        }
    }
}
