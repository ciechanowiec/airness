package eu.ciechanowiec.airness.governance;

/**
 * What a project is allowed to do with a file the harness owns.
 *
 * <p>The first three answers exist because the files divide three ways, and the division is decided by who
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
     * The file must exist and is written whenever absent, but its content is never compared. Its body is
     * per-project by construction, so an exact-match check on it would fail on the first project that
     * used it.
     */
    SEED,

    /**
     * Written only during explicit initialization, then wholly owned by the project. Neither a later sync
     * nor verification recreates or requires it, so a project can remove an inapplicable workflow.
     */
    SCAFFOLD,

    /**
     * The file must not exist in the project. The harness supplies it from a jar or unpacks it under the
     * build directory, and a copy in the tree is a copy that will be edited and then ignored.
     */
    FORBIDDEN,

    /**
     * The file carries one marked block for each capability that claims it, and the blocks lead the file
     * in capability order. Every line below them belongs to the project. The capabilities that claim it
     * share it rather than override each other, which is how a root file serves a whole repository.
     */
    SECTION,

    /**
     * The file is optional, and when present it says nothing but where the instructions are: every line
     * that says anything names {@code AGENTS.md}. A tool-specific instruction file that states rules of its
     * own holds one agent to a second contract.
     */
    REFERENCE
}
