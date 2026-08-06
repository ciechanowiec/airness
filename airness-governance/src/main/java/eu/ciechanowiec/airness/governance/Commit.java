package eu.ciechanowiec.airness.governance;

/**
 * One commit reachable from the history: its hash, parsed {@link CommitMessage}, change statistics,
 * and whether its topology makes it a merge.
 */
record Commit(String sha, CommitMessage message, DiffStat stat, boolean merge) {
}
