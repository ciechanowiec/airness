package eu.ciechanowiec.airness.governance;

/**
 * One test method, located as a region of the source that declares it.
 *
 * <p>The region is held as a pair of offsets rather than as the text it spans, because the same region
 * is read from two renderings of one source: once with every literal blanked, to find the calls the
 * body makes, and once with its one-line literals intact, to read the operands those calls were given.
 * Both renderings keep the width of the original, so one pair of offsets addresses both.
 *
 * @param name  the declared name of the method, which is what a report has to carry to be actionable
 * @param start the offset just past the brace that opens the body
 * @param end   the offset of the brace that closes it
 */
record CaseBody(String name, int start, int end) {

    /**
     * This body as the given rendering of its source spells it.
     *
     * @param source a rendering of the source this method was located in, at its original width
     * @return the body
     */
    String in(String source) {
        return source.substring(this.start, this.end);
    }
}
