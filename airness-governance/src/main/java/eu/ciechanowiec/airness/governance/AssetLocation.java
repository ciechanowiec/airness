package eu.ciechanowiec.airness.governance;

/**
 * Where a managed file is placed.
 */
public enum AssetLocation {

    /**
     * The repository root, for a file that git, an editor, Maven or an agent reads there.
     */
    ROOT,

    /**
     * Each declared node project directory, for a file that the node tools read beside a
     * {@code package.json}.
     */
    NODE_PROJECT
}
