package eu.ciechanowiec.airness.governance;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * An attribute the Thymeleaf standard dialect processes, written under the {@code th:} prefix or under the
 * {@code data-th-} spelling a document uses when it has to stay valid HTML5, which no dialect prefix is.
 */
@UtilityClass
final class ThymeleafAttribute {

    private static final String PREFIX = "th:";

    private static final String DATA_PREFIX = "data-th-";

    /**
     * Whether the dialect processes an attribute of this name, under either spelling.
     *
     * @param name the attribute name, in the case the caller compares in
     * @return whether the name carries the prefix or the data spelling
     */
    static boolean isDialect(String name) {
        return name.startsWith(PREFIX) || name.startsWith(DATA_PREFIX);
    }

    /**
     * Both spellings of each named dialect attribute.
     *
     * @param locals the attribute names after the prefix, such as {@code replace}
     * @return every {@code th:} and {@code data-th-} spelling of them
     */
    static Set<String> spellings(String... locals) {
        return Stream.of(locals).flatMap(local -> Stream.of(PREFIX + local, DATA_PREFIX + local))
            .collect(Collectors.toUnmodifiableSet());
    }
}
