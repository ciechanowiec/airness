package eu.ciechanowiec.airness.governance;

/**
 * One opening tag, closing tag or comment of a pom, with the text written between it and the markup before it.
 *
 * <p>An empty element written as {@code <relativePath/>} is an opening tag directly followed by its closing tag,
 * with nothing between them.
 *
 * @param kind      whether it opens an element, closes one, or is a comment
 * @param name      the element's name, empty for a comment
 * @param depth     how many elements enclose it, the root element standing at zero and its children at one
 * @param line      the one-based line it starts on
 * @param gapBefore the text between it and the markup before it
 */
record PomTag(Kind kind, String name, int depth, int line, String gapBefore) {

    /**
     * How an offence names it: {@code <name>}, {@code </name>} or {@code a comment}.
     *
     * @return the label
     */
    String label() {
        return switch (this.kind) {
            case OPEN -> '<' + this.name + '>';
            case CLOSE -> "</" + this.name + '>';
            case COMMENT -> "a comment";
        };
    }

    /**
     * Whether it is a tag of the given kind at the given depth.
     *
     * @param expected      the kind asked about
     * @param expectedDepth the depth asked about
     * @return whether both match
     */
    boolean is(Kind expected, int expectedDepth) {
        return this.kind == expected && this.depth == expectedDepth;
    }

    /**
     * Whether a line break stands between it and the markup before it, so that it starts a line of its own.
     *
     * @return whether it starts its own line
     */
    boolean breaksLine() {
        return this.gapBefore.indexOf('\n') >= 0;
    }

    /**
     * How many line breaks stand between it and the markup before it, two being one blank line.
     *
     * @return the count
     */
    int newlinesBefore() {
        return (int) this.gapBefore.chars().filter(character -> character == '\n').count();
    }

    /**
     * Whether only whitespace holding at least one blank line stands between it and the markup before it.
     *
     * @return whether a blank line precedes it
     */
    boolean blankBefore() {
        return this.gapBefore.isBlank() && this.newlinesBefore() > 1;
    }

    /**
     * The first blank line between it and the markup before it.
     *
     * @return the one-based line
     */
    int firstBlankLine() {
        return this.line - this.newlinesBefore() + 1;
    }

    /**
     * What a piece of pom markup is.
     */
    enum Kind {

        /**
         * An opening tag, or the whole of an empty element.
         */
        OPEN,

        /**
         * A closing tag, or the end of an empty element.
         */
        CLOSE,

        /**
         * A comment.
         */
        COMMENT;

        /**
         * The depth of markup of this kind met while the given number of elements are open.
         *
         * @param open how many elements are open before it
         * @return its depth
         */
        int depthOf(int open) {
            return this == CLOSE ? open - 1 : open;
        }

        /**
         * How many elements are open after markup of this kind.
         *
         * @param open how many elements are open before it
         * @return how many are open after it
         */
        int openAfter(int open) {
            return switch (this) {
                case OPEN -> open + 1;
                case CLOSE -> open - 1;
                case COMMENT -> open;
            };
        }
    }
}
