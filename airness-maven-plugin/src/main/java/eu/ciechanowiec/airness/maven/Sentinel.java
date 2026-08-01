package eu.ciechanowiec.airness.maven;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Reads a comma-separated parameter, and tells an empty one from a declared absence.
 *
 * <p>An unset list parameter and a list parameter someone meant to leave empty look identical by the
 * time a check reads them, and both make the check pass by scanning nothing. That is the failure this
 * exists to prevent, so the empty case has to be spelled: {@code NONE} says the project has none of
 * whatever the parameter names, and anything blank says nobody decided.
 */
final class Sentinel {

    /**
     * The literal a project writes to say it has none of what a parameter names.
     */
    static final String NONE = "NONE";

    private static final String SEPARATOR = ",";

    private Sentinel() {
        throw new UnsupportedOperationException("This class is not meant to be instantiated");
    }

    /**
     * The value a parameter declares, or nothing when it declares that there is none.
     *
     * @param value the raw parameter value
     * @return the value, or nothing when it is the {@code NONE} sentinel
     * @throws IllegalStateException when the value is blank, which decides nothing either way
     */
    static Optional<String> declared(String value) {
        return Optional.of(required(value)).filter(stated -> !NONE.equals(stated));
    }

    /**
     * The entries a parameter declares, or nothing when it declares that there are none.
     *
     * @param value the raw parameter value
     * @return the entries, or nothing when the value is the {@code NONE} sentinel
     * @throws IllegalStateException when the value is blank, which decides nothing either way
     */
    static Optional<List<String>> declaredList(String value) {
        return declared(value).map(Sentinel::entries);
    }

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

    private static String required(String value) {
        return Optional.ofNullable(value).map(String::strip).filter(text -> !text.isEmpty()).orElseThrow(
            () -> new IllegalStateException(
                "The parameter is unset. Give it a value, or the literal " + NONE + " to declare it has none"
            )
        );
    }

    private static List<String> entries(String value) {
        return Arrays.stream(value.split(SEPARATOR))
            .map(String::strip)
            .filter(entry -> !entry.isEmpty())
            .toList();
    }
}
