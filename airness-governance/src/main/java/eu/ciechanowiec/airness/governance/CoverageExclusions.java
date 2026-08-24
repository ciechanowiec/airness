package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * The shape a coverage exclusion is allowed to take.
 *
 * <p>A file leaves the coverage floor by what it is, never by what it is called. Generated code is a role,
 * and so is a framework entry point that a test cannot construct. One class a test does not reach is not
 * a role, it is a gap with a name. The difference matters because a floor that any single class can be
 * lifted out of is a floor that reports the coverage of whatever was easy to cover.
 *
 * <p>What this can decide is the shape of the pattern, not the intent behind it. A pattern shaped like a
 * role can still be written narrowly enough to match one class, and this says nothing about that. What it
 * removes is the form that names a class outright, and with it the reading that the harness endorsed
 * doing so.
 */
@UtilityClass
public final class CoverageExclusions {

    private static final String ROLE = "*";
    private static final String LOCATION = "**";

    /**
     * Reads one comma-separated exclusion setting.
     *
     * @param setting the declared value
     * @return one problem per entry that names something other than a role
     */
    public static Stream<String> problems(String setting) {
        return Stream.of(setting.split(","))
            .map(String::strip)
            .filter(entry -> !roleShaped(entry))
            .map(CoverageExclusions::problem);
    }

    /**
     * Whether one entry names a file role.
     *
     * @param entry a single exclusion pattern
     * @return whether the harness will read it as a role
     */
    public static boolean roleShaped(String entry) {
        List<String> segments = List.of(entry.split("/", -1));
        String last = segments.getLast();
        return !entry.isEmpty() && namesARole(last);
    }

    private static boolean namesARole(String segment) {
        return segment.contains(ROLE) && !ROLE.equals(segment) && !LOCATION.equals(segment);
    }

    private static String problem(String entry) {
        return "Rewrite child coverage exclusion \"" + entry
            + "\"; exclude by file role such as **/*Mojo*, never by naming a class";
    }
}
