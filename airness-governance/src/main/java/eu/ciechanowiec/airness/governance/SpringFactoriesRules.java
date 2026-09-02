package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Reports registration keys Spring Boot has stopped reading from {@code spring.factories}.
 *
 * <p>The file is parsed with {@link Properties}, which is the grammar Spring itself reads: escaped
 * separators, continuations and Unicode escapes therefore mean the same thing here that they mean to
 * the framework. The one removed key has one unambiguous replacement resource.
 */
@UtilityClass
final class SpringFactoriesRules {

    private static final String AUTO_CONFIGURATION
        = "org.springframework.boot.autoconfigure.EnableAutoConfiguration";

    /**
     * Whether a {@code spring.factories} resource declares the removed auto-configuration key.
     *
     * @param path    repository-relative path named by an offence
     * @param content resource content
     * @return the offence, when the removed key is present
     */
    static List<String> unsupported(Path path, CharSequence content) {
        return properties(content).containsKey(AUTO_CONFIGURATION)
            ? List.of(
                path + ": " + AUTO_CONFIGURATION
                    + " is no longer read from spring.factories; move its candidates to"
                    + " META-INF/spring/org.springframework.boot.autoconfigure."
                    + "AutoConfiguration.imports"
            )
            : List.of();
    }

    private static Map<Object, Object> properties(CharSequence content) {
        return loaded(new Properties(), content);
    }

    @SneakyThrows(IOException.class)
    private static Map<Object, Object> loaded(
        Map<Object, Object> factories, CharSequence content
    ) {
        ((Properties) factories).load(new StringReader(content.toString()));
        return factories;
    }
}
