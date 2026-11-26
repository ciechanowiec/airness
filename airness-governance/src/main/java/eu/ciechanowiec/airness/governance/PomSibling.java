package eu.ciechanowiec.airness.governance;

/**
 * One element or comment written directly inside {@code <project>}, with the whitespace that precedes it.
 *
 * @param label          how an offence names it: the element's tag, or {@code a comment}
 * @param comment        whether it is a comment
 * @param block          whether it is an element that holds elements, which the pom's blank lines set apart
 * @param line           the one-based line it starts on
 * @param newlinesBefore how many line breaks stand between it and the markup before it, two being one blank line
 */
record PomSibling(String label, boolean comment, boolean block, int line, int newlinesBefore) {
}
