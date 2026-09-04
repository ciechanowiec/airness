package eu.ciechanowiec.airness.governance;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Resolves the one form of variable a compose file or a Dockerfile can resolve on its own: a variable
 * written with a default, as {@code ${TAG:-7.0.14}} or {@code ${TAG-7.0.14}}.
 *
 * <p>The default is what the file pulls on a machine where nothing set the variable, which is the only
 * machine a rule can reason about. A variable written without one stays in the reference, and the rule
 * that reads the reference reports it as one it cannot judge rather than guessing.
 */
@UtilityClass
final class VariableDefaults {

    private static final Pattern DEFAULTED = Pattern.compile("\\$\\{(?<name>[A-Za-z_]\\w*):?-(?<value>[^}]*)}");

    /**
     * The text with every defaulted variable replaced by its default.
     *
     * @param text the text as written
     * @return the text as it reads where no variable is set
     */
    static String applied(String text) {
        return DEFAULTED.matcher(text).replaceAll(match -> Matcher.quoteReplacement(match.group("value")));
    }
}
