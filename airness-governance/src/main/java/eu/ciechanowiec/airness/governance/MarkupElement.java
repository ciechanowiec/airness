package eu.ciechanowiec.airness.governance;

import java.util.Map;

/**
 * One element of a markup document, as the check reading it sees it.
 */
@FunctionalInterface
interface MarkupElement {

    /**
     * Reads one element.
     *
     * @param attributes what the element carries, which is empty rather than absent
     * @param line       the line the element was written on
     * @param column     the column the element was written at
     */
    void read(Map<String, String> attributes, int line, int column);

    /**
     * Reads the text written between elements, which most checks have no question about.
     *
     * <p>An expression is written in a document's text as readily as in an attribute, and the two are
     * the same construct to the engine. A check that read attributes alone would therefore answer for
     * half a document while reading as though it read all of it, which is why the text is offered here
     * rather than in a second scan that could fall out of step with this one.
     *
     * <p>Doing nothing is the default because a rule about what an element carries has no question to
     * ask of the prose between two of them, and the checks written before this one ask none.
     *
     * @param content the text as it was written
     * @param line    the line the text began on
     * @param column  the column the text began at
     */
    default void text(String content, int line, int column) {
        // A rule about attributes has nothing to ask of the text, and says so by not overriding this.
    }
}
