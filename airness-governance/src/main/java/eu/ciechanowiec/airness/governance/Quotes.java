package eu.ciechanowiec.airness.governance;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Removes the quotation marks a YAML or Dockerfile value may be written in, so a rule compares the
 * value and not its spelling.
 */
@UtilityClass
final class Quotes {

    private static final Pattern QUOTED = Pattern.compile("^([\"'])(?<inner>.*)\\1$");

    /**
     * The value without a matching pair of surrounding quotation marks, stripped of blank space.
     *
     * @param value the value as written
     * @return the value as meant
     */
    static String stripped(String value) {
        String text = value.strip();
        Matcher matched = QUOTED.matcher(text);
        return matched.matches() ? matched.group("inner") : text;
    }
}
