package eu.ciechanowiec.airness.governance;

import java.util.OptionalInt;

/**
 * One significant line of a YAML file, read for what decides how the lines after it nest.
 *
 * <p>The content column is where the line's node starts: the indentation of a plain line, and past the
 * dash and its spaces for a sequence entry, so that the lines continuing an entry at that column read as
 * its siblings rather than as a step. A bare dash has no node on its line, nor has a dash followed only by an
 * anchor or a tag, and the content column of either is the dash itself, which is what the entry's first nested
 * line steps from.
 *
 * @param number          the one-based line number
 * @param indent          how many spaces open the line
 * @param contentColumn   the column nested lines step from
 * @param opensBlock      whether the line ends in a key, a bare dash, or either followed only by an anchor or
 *                        a tag, so that a deeper line after it is a nesting step
 * @param scalarThreshold the column at or before which a block scalar the line opens ends, or {@link #NONE}
 * @param flowDepth       how many flow collections the line opens and leaves unclosed
 */
record YamlLine(int number, int indent, int contentColumn, boolean opensBlock, int scalarThreshold, int flowDepth) {

    /**
     * The scalar threshold of a line that opens no block scalar.
     */
    static final int NONE = -1;

    /**
     * What stands before the first line of a document: no line is deeper than it, so the first line is
     * never read as a step or a continuation.
     */
    static final YamlLine START = new YamlLine(0, 0, Integer.MAX_VALUE, false, NONE, 0);

    private static final char DASH = '-';

    /**
     * Reads one significant line: neither blank, nor a comment, nor a document marker.
     *
     * @param line   the line as written
     * @param number its one-based line number
     * @return what the line decides about the nesting after it
     */
    static YamlLine of(String line, int number) {
        String text = YamlText.uncommented(line);
        int indent = YamlText.indent(text);
        int start = YamlText.nodeStart(text, indent);
        LineNode node = new LineNode(
            text.substring(start), start, start > indent ? text.lastIndexOf(DASH, start - 1) : indent
        );
        String value = node.value();
        return new YamlLine(
            number, indent, node.contentColumn(value), value.isEmpty(), node.scalarThreshold(value), flowDepth(value)
        );
    }

    private static int flowDepth(String value) {
        return YamlText.flow(value) ? Math.max(0, YamlText.balance(value)) : 0;
    }

    /**
     * The node a line holds, past the sequence entries that open the line.
     *
     * @param text  the node, from where it starts
     * @param start the column it starts at
     * @param dash  the column of the last dash before it, or the line's indentation when no dash opens the line
     */
    private record LineNode(String text, int start, int dash) {

        // A flow collection is read whole as the value, so a colon inside one is no key of the line.
        private OptionalInt separator() {
            return YamlText.flow(this.text) ? OptionalInt.empty() : YamlText.mappingIndicator(this.text);
        }

        private boolean keyed() {
            return this.separator().isPresent();
        }

        private String value() {
            OptionalInt separator = this.separator();
            return YamlText.withoutProperties(
                separator.isPresent() ? this.text.substring(separator.getAsInt() + 1) : this.text
            );
        }

        // A key and a value start where the node does. A dash with nothing after it but an anchor or a tag holds
        // its node on the lines below, which step from the dash.
        private int contentColumn(String value) {
            return this.keyed() || !value.isEmpty() ? this.start : this.dash;
        }

        // The text of a block scalar is whatever lies deeper than the node that owns it: the key it is the value
        // of, or else the dash it is the entry of.
        private int scalarThreshold(CharSequence value) {
            if (!YamlText.blockScalar(value)) {
                return NONE;
            }
            return this.keyed() ? this.start : this.dash;
        }
    }
}
