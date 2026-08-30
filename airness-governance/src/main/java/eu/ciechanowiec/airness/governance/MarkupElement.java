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
}
