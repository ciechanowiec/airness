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
    NODE_PROJECT,

    /**
     * Each declared node project that is a Next.js application, because its {@code package.json} installs
     * {@code next}, whose routes live under {@code src/app} or {@code src/pages}, and which runs no middleware or
     * proxy of its own, for a file only the Next.js facet needs there. The facet switches on by detection, never
     * by a flag.
     */
    NEXT_PROJECT
}
