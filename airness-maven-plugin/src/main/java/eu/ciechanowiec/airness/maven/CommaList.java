package eu.ciechanowiec.airness.maven;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * A comma-separated parameter, read into its entries.
 */
@UtilityClass
final class CommaList {

    private static final String SEPARATOR = ",";

    /**
     * The entries of an optional comma-separated parameter, which are none when nothing is set.
     *
     * <p>An unset parameter reads as no entries rather than as a refusal, because an empty list of exemptions
     * exempts nothing and is therefore the safe reading rather than the silent one.
     *
     * @param value the raw parameter value
     * @return the entries, stripped and without empty segments, empty when nothing was set
     */
    static List<String> entries(@Nullable String value) {
        return Optional.ofNullable(value).map(String::strip).filter(text -> !text.isEmpty())
            .map(CommaList::split)
            .orElseGet(List::of);
    }

    private static List<String> split(String value) {
        return Arrays.stream(value.split(SEPARATOR, -1))
            .map(String::strip)
            .filter(entry -> !entry.isEmpty())
            .toList();
    }
}
