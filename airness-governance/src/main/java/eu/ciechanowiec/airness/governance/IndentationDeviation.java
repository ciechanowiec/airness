package eu.ciechanowiec.airness.governance;

/**
 * The first place a file's indentation departs from the {@code .editorconfig} it answers to.
 *
 * @param line        the one-based line that departs
 * @param explanation the property the line breaks and how, naming the line, such as
 *                    {@code indent_style = space, but line 12 indents with a tab}
 */
record IndentationDeviation(int line, String explanation) {
}
