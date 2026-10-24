package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;

/**
 * One file the harness owns, the policy that says what a project may do with it, the capability that
 * owns it, and where it is placed.
 *
 * @param path     the path the file takes, relative to its location
 * @param policy   what the project may do with it
 * @param owner    the capability whose manifest claims it
 * @param location where it is placed
 */
public record ManagedAsset(String path, AssetPolicy policy, Capability owner, AssetLocation location) {

    // Reject paths that could escape or ambiguously address the repository root. The unsafe test runs
    // first and on its own, because a root path has no name at index zero and asking for one throws an
    // exception carrying none of the explanation below.
    public ManagedAsset {
        Path parsed = Path.of(path);
        boolean unsafe = path.isBlank() || path.contains("\\") || parsed.isAbsolute();
        boolean ambiguous = !unsafe
            && ("..".equals(parsed.getName(0).toString()) || !parsed.normalize().equals(parsed));
        if (unsafe || ambiguous) {
            throw new IllegalArgumentException("Managed asset path must be canonical and repository-relative: " + path);
        }
    }

    /**
     * A repository-root file of the generic layer.
     *
     * @param path   the repository-relative path
     * @param policy what the project may do with it
     */
    public ManagedAsset(String path, AssetPolicy policy) {
        this(path, policy, Capability.GENERIC, AssetLocation.ROOT);
    }
}
