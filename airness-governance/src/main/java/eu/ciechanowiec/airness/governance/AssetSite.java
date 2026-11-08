package eu.ciechanowiec.airness.governance;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * One directory that receives managed files, with the layers present there.
 *
 * <p>The repository root is one site, carrying every layer present anywhere in the repository, so
 * its shared files hold a block for each. Each declared node project is another, carrying only what that
 * project is.
 *
 * @param directory    the directory, relative to the repository root, and empty for the root itself
 * @param location     which manifest rows apply here
 * @param layers the layers present here
 */
public record AssetSite(String directory, AssetLocation location, Set<Layer> layers) {

    /**
     * Copies the layers, so the site cannot change after it was described.
     *
     * @param directory    the directory, relative to the repository root, and empty for the root itself
     * @param location     which manifest rows apply here
     * @param layers the layers present here
     */
    public AssetSite {
        layers = Set.copyOf(layers);
    }

    /**
     * The repository root of a repository holding the given layers.
     *
     * @param layers every layer present anywhere in the repository
     * @return the root site, which always carries the generic layer
     */
    public static AssetSite root(Set<Layer> layers) {
        return new AssetSite(
            "",
            AssetLocation.ROOT,
            Stream.concat(layers.stream(), Stream.of(Layer.GENERIC)).collect(Collectors.toSet())
        );
    }

    /**
     * The repository-relative path a managed file takes at this site.
     *
     * @param asset the managed file
     * @return its path from the repository root
     */
    String file(ManagedAsset asset) {
        return this.directory.isEmpty() ? asset.path() : this.directory + '/' + asset.path();
    }
}
