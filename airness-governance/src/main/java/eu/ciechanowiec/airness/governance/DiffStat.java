package eu.ciechanowiec.airness.governance;

/**
 * The size of a change: the number of files it touches and the number of added plus deleted lines.
 * Used to decide whether a commit crosses the triviality threshold and so requires an explanatory body.
 */
record DiffStat(int changedFiles, int changedLines) {
}
