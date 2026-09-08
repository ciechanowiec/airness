package eu.ciechanowiec.airness.spring;

import java.util.Locale;
import org.springframework.context.MessageSource;

/**
 * Distinguishes an absent key from a valid empty or key-shaped translation.
 */
final class SpringMessageLookup {

    private static final String FIRST = "airness-absent-message-first";
    private static final String SECOND = "airness-absent-message-second";
    private final MessageSource source;

    SpringMessageLookup(MessageSource source) {
        this.source = source;
    }

    boolean missing(String key) {
        String first = this.source.getMessage(key, new Object[0], FIRST, Locale.ROOT);
        String second = this.source.getMessage(key, new Object[0], SECOND, Locale.ROOT);
        return FIRST.equals(first) && SECOND.equals(second);
    }
}
