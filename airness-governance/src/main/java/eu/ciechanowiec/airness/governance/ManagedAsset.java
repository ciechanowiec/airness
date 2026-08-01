package eu.ciechanowiec.airness.governance;

/**
 * One file the harness owns, and the policy that says what a project may do with it.
 *
 * @param path   the repository-relative path the file takes in a project
 * @param policy what the project may do with it
 */
public record ManagedAsset(String path, AssetPolicy policy) {
}
