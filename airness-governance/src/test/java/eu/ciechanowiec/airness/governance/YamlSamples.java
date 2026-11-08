package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import lombok.experimental.UtilityClass;

/**
 * Asks how a YAML sample nests under the step width the repository's {@code .editorconfig} gives YAML.
 */
@UtilityClass
final class YamlSamples {

    private static final int SIZE = 2;

    /**
     * Asserts that every step of the sample is the declared width.
     *
     * @param text the sample
     */
    static void assertClean(String text) {
        assertEquals(Optional.empty(), first(text), text);
    }

    /**
     * Asserts that the first step of another width is on the given line.
     *
     * @param line the one-based line expected to depart
     * @param text the sample
     */
    static void assertDeviationAt(int line, String text) {
        assertEquals(line, first(text).map(IndentationDeviation::line).orElse(0), text);
    }

    /**
     * The first step of another width in the sample.
     *
     * @param text the sample
     * @return the departure, or nothing
     */
    static Optional<IndentationDeviation> first(String text) {
        return YamlNesting.first(text.lines().toList(), SIZE);
    }
}
