package eu.ciechanowiec.airness.maven;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;

/**
 * Reads an optional comma-separated parameter.
 */
@UtilityClass
final class Sentinel {

    private static final String SEPARATOR = ",";

    /**
     * The entries of an optional list, which is empty when nothing is set.
     *
     * <p>No sentinel is needed here, because an empty list of exemptions exempts nothing and is
     * therefore the safe reading rather than the silent one.
     *
     * @param value the raw parameter value
     * @return the entries, empty when nothing was set
     */
    static List<String> optional(String value) {
        return Optional.ofNullable(value).map(String::strip).filter(text -> !text.isEmpty())
            .map(Sentinel::entries)
            .orElseGet(List::of);
    }

    private static List<String> entries(String value) {
        return Arrays.stream(value.split(SEPARATOR, -1))
            .map(String::strip)
            .filter(entry -> !entry.isEmpty())
            .toList();
    }
}
