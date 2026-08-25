package eu.ciechanowiec.airness.governance;

/**
 * A commit message split into its header (the first line) and its body (everything after that line),
 * both already stripped of surrounding whitespace.
 *
 * <p>The split is the first newline rather than the first blank line. A blank line between the two is
 * the Git convention and reads better, but it is not what divides them here, so an explanation written
 * straight under the header still counts as a body.
 */
record CommitMessage(String header, String body) {
}
