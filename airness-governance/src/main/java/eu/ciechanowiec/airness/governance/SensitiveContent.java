package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Recognizable credential material that must not enter a distributable artifact.
 */
@UtilityClass
final class SensitiveContent {

    private static final List<Pattern> SECRET_PATTERNS = List.of(
        Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----"),
        Pattern.compile("AKIA[0-9A-Z]{16}"),
        Pattern.compile("gh[pousr]_[A-Za-z0-9]{20,}")
    );

    static boolean secret(CharSequence content) {
        return SECRET_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(content).find());
    }
}
