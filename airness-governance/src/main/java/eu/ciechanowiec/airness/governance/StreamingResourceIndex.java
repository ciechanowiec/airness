package eu.ciechanowiec.airness.governance;

import lombok.experimental.UtilityClass;

/**
 * Recognizes the production property without treating arbitrary configuration as a timeout declaration.
 */
@UtilityClass
public final class StreamingResourceIndex {

    /**
     * Reads the same canonical setting names as the existing Spring configuration check.
     *
     * @param name    the configuration filename
     * @param content the copied production configuration
     * @return whether the file declares the MVC asynchronous timeout
     */
    public static boolean declares(String name, CharSequence content) {
        return new SpringConfiguration(name, content).declared("spring.mvc.async.request-timeout").isPresent();
    }
}
