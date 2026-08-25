package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;

/**
 * One file the harness owns, and the policy that says what a project may do with it.
 *
 * @param path   the repository-relative path the file takes in a project
 * @param policy what the project may do with it
 */
public record ManagedAsset(String path, AssetPolicy policy) {

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
}
