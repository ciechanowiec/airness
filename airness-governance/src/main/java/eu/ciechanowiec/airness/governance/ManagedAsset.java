package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;

/**
 * One file the harness owns, and the policy that says what a project may do with it.
 *
 * @param path   the repository-relative path the file takes in a project
 * @param policy what the project may do with it
 */
public record ManagedAsset(String path, AssetPolicy policy) {

    /** Rejects paths that could escape or ambiguously address the repository root. */
    public ManagedAsset {
        Path parsed = Path.of(path);
        if (path.isBlank() || path.contains("\\") || parsed.isAbsolute()
            || parsed.getName(0).toString().equals("..")
            || !parsed.normalize().toString().equals(path)) {
            throw new IllegalArgumentException("Managed asset path must be canonical and repository-relative: " + path);
        }
    }
}
