package eu.ciechanowiec.airness.governance;

/**
 * What a project is allowed to do with a file the harness owns.
 *
 * <p>The three answers exist because the files divide three ways, and the division is decided by who
 * reads the file rather than by how important it is. A tool outside Maven that reads a path from disk
 * leaves no choice but a real file in the tree, which is the pinned case. A file whose body is
 * per-project can only be started off, which is the seed case. Everything else the build can supply from
 * a jar or an unpacked directory, and a copy of it sitting in the tree is worse than no copy, because it
 * looks authoritative while nothing reads it.
 */
public enum AssetPolicy {

    /**
     * The file exists in the project with exactly the bytes the harness ships. Something outside Maven
     * reads it from disk, so there is nowhere else for it to live.
     */
    PINNED,

    /**
     * The file is written once when absent, and never looked at again. Its body is per-project by
     * construction, so an exact-match check on it would fail on the first project that used it.
     */
    SEED,

    /**
     * The file must not exist in the project. The harness supplies it from a jar or unpacks it under the
     * build directory, and a copy in the tree is a copy that will be edited and then ignored.
     */
    FORBIDDEN
}
