package eu.ciechanowiec.airness.governance;

import lombok.experimental.UtilityClass;

/**
 * Reads whether a configuration value takes everything it holds from the environment.
 *
 * <p>A value qualifies when it is nothing but placeholders, none of which falls back to a literal. A default
 * written after the colon is what the application runs with wherever the variable is unset, so it ships
 * inside the artifact as surely as a value written outright. An empty default carries nothing, and a default
 * that is itself such a placeholder reads the environment again.
 */
@UtilityClass
final class SpringPlaceholders {

    private static final String OPENING = "${";
    private static final char OPENS = '{';
    private static final char CLOSES = '}';
    private static final char FALLBACK = ':';

    /**
     * Answers whether the given value is nothing but placeholders, none of which falls back to a literal.
     *
     * @param text the value as written, without its quotation marks
     * @return whether the value carries nothing of its own
     */
    static boolean environmentOnly(String text) {
        return text.isEmpty() || opened(text);
    }

    // Whether the text opens a placeholder that closes without a literal default, followed by nothing but
    // such placeholders.
    private static boolean opened(String text) {
        return text.startsWith(OPENING) && closed(text.substring(OPENING.length()));
    }

    // Whether the text after an opening closes a placeholder without a literal default, followed by nothing
    // but such placeholders.
    private static boolean closed(String inside) {
        int closes = depthless(inside, CLOSES);
        return closes >= 0 && defaultless(inside.substring(0, closes))
            && environmentOnly(inside.substring(closes + 1));
    }

    private static boolean defaultless(String inner) {
        int fallback = depthless(inner, FALLBACK);
        return fallback < 0 || environmentOnly(inner.substring(fallback + 1));
    }

    // The first occurrence of a character outside every brace the text opens, or -1 when there is none.
    private static int depthless(String text, char wanted) {
        int depth = 0;
        for (int at = 0; at < text.length(); at++) {
            char current = text.charAt(at);
            if (current == wanted && depth == 0) {
                return at;
            }
            depth += step(current);
        }
        return -1;
    }

    private static int step(char current) {
        return (current == OPENS ? 1 : 0) - (current == CLOSES ? 1 : 0);
    }
}
