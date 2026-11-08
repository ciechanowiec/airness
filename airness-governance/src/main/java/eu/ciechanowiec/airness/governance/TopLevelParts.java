package eu.ciechanowiec.airness.governance;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * The comma-separated parts of a rule map that sit outside every bracket and string, so the options of one
 * rule, which carry commas and colons of their own, stay with the rule they configure.
 *
 * <p>One pass over the map, keeping how deep inside brackets it stands and which quotation mark, if any, is
 * open. A comma read at depth zero outside a string ends a part, and every other character belongs to the
 * part it falls in.
 */
@UtilityClass
final class TopLevelParts {

    private static final String OPENING = "[{(";
    private static final String CLOSING = "]})";
    private static final String QUOTES = "\"'";
    private static final char SEPARATOR = ',';
    private static final char UNQUOTED = 0;

    /**
     * Splits a rule map at its top-level commas.
     *
     * @param map the body of a rule map, without its reason
     * @return the parts in map order, the last one included even when it is empty
     */
    static List<String> of(String map) {
        List<String> parts = new ArrayList<>();
        Position position = new Position(0, UNQUOTED);
        int start = 0;
        for (int index = 0; index < map.length(); index++) {
            char character = map.charAt(index);
            if (position.separates(character)) {
                parts.add(map.substring(start, index));
                start = index + 1;
            }
            position = position.after(character);
        }
        parts.add(map.substring(start));
        return List.copyOf(parts);
    }

    /**
     * Where a reader stands in a rule map.
     *
     * @param depth how many brackets are open
     * @param quote the quotation mark of the open string, or {@link #UNQUOTED} outside every string
     */
    private record Position(int depth, char quote) {

        boolean separates(char character) {
            return this.quote == UNQUOTED && this.depth == 0 && character == SEPARATOR;
        }

        // Inside a string only its own closing mark matters, and outside one a mark opens it and a bracket
        // moves the depth.
        Position after(char character) {
            if (this.quote != UNQUOTED) {
                return character == this.quote ? new Position(this.depth, UNQUOTED) : this;
            }
            return this.entered(character);
        }

        private Position entered(char character) {
            return QUOTES.indexOf(character) >= 0
                ? new Position(this.depth, character)
                : new Position(this.depth + depthChange(character), this.quote);
        }

        private static int depthChange(char character) {
            boolean opens = OPENING.indexOf(character) >= 0;
            boolean closes = CLOSING.indexOf(character) >= 0;
            return Boolean.compare(opens, closes);
        }
    }
}
