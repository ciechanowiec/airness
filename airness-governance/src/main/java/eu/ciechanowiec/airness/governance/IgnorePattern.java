package eu.ciechanowiec.airness.governance;

/**
 * One pattern line of an ignore file, as git and Docker read it.
 *
 * <p>The text is the line without the trailing spaces both tools drop, so two lines that differ only there are the
 * same pattern. A space a backslash escapes is part of the pattern and stays.
 *
 * @param file  the ignore file, relative to the working tree root and separated by forward slashes
 * @param line  the line number, counted from one
 * @param text  the pattern as the tool reads it, with a leading {@code !} when it negates
 * @param block the layer whose Airness managed block holds the line, or empty for a line the project wrote
 */
record IgnorePattern(String file, int line, String text, String block) {

    private static final String NEGATION = "!";
    private static final String SEPARATOR = "/";

    /**
     * Whether the line re-includes what an earlier pattern excluded.
     *
     * @return whether the pattern negates
     */
    boolean negated() {
        return this.text.startsWith(NEGATION);
    }

    /**
     * Whether git applies the pattern at every depth beneath its file, which it does for a pattern with no
     * separator but an optional trailing one. A pattern with a separator at its start or in its middle is
     * relative to the directory of its own file, so the same text in two files names two different paths.
     *
     * @return whether the pattern is un-anchored
     */
    boolean unanchored() {
        String body = this.negated() ? this.text.substring(NEGATION.length()) : this.text;
        String directoryless = body.length() > SEPARATOR.length() && body.endsWith(SEPARATOR)
            ? body.substring(0, body.length() - SEPARATOR.length())
            : body;
        return !directoryless.contains(SEPARATOR);
    }

    /**
     * Whether the line stands in an Airness managed block, which the project cannot edit.
     *
     * @return whether the line is managed
     */
    boolean managed() {
        return !this.block.isEmpty();
    }

    /**
     * The directory of the line's file, which an anchored pattern is relative to.
     *
     * @return the directory, relative to the working tree root and empty for the root itself
     */
    String directory() {
        return IgnoreFile.directoryOf(this.file);
    }

    /**
     * Where the line is declared, as a finding names it.
     *
     * @return the file and line, and the managed block that holds the line, if any
     */
    String location() {
        String place = this.file + ':' + this.line;
        return this.managed() ? place + " in the Airness managed " + this.block + " block" : place;
    }
}
