package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * The shape a coverage exclusion is allowed to take, and the shape that would do nothing at all.
 *
 * <p>A file leaves the coverage floor by what it is, never by what it is called. Generated code is a role,
 * and so is a framework entry point that a test cannot construct. One class a test does not reach is not
 * a role, it is a gap with a name. The difference matters because a floor that any single class can be
 * lifted out of is a floor that reports the coverage of whatever was easy to cover.
 *
 * <p>The separator is the other half, and it is the half that fails quietly. The coverage tool matches a
 * pattern against the VM name of a class, where packages are separated by a slash and a nested class by a
 * dollar sign. A name written the way it is written in source, with dots, matches nothing: the pattern is
 * accepted, the setting reads as though it excluded something, and every class it named is measured
 * anyway. Nothing reports that, because from the tool's side there is nothing to report. A pattern that
 * cannot match is therefore a finding here rather than a setting that sits in a project file for years
 * while the report it was meant to change looks exactly the same either way.
 *
 * <p>What this cannot decide is the intent behind a pattern. One shaped like a role can still be written
 * narrowly enough to match a single class, and this says nothing about that. What it removes is the form
 * that names a class outright, and with it the reading that the harness endorsed doing so.
 */
@UtilityClass
public final class CoverageExclusions {

    private static final String ROLE = "*";
    private static final String LOCATION = "**";
    private static final String SOURCE_SEPARATOR = ".";

    /**
     * Reads one comma-separated exclusion setting.
     *
     * @param setting the declared value
     * @return every problem across its entries, in the order the entries are written
     */
    public static Stream<String> problems(String setting) {
        return Stream.of(setting.split(","))
            .map(String::strip)
            .flatMap(CoverageExclusions::problemsIn);
    }

    /**
     * Whether one entry names a file role.
     *
     * @param entry a single exclusion pattern
     * @return whether the harness will read it as a role
     */
    public static boolean roleShaped(String entry) {
        List<String> segments = List.of(entry.split("/", -1));
        return !entry.isEmpty() && namesARole(segments.getLast());
    }

    /**
     * Whether one entry can match a class at all.
     *
     * @param entry a single exclusion pattern
     * @return whether the coverage tool could ever read it as naming something
     */
    public static boolean matchable(String entry) {
        return !entry.contains(SOURCE_SEPARATOR);
    }

    private static Stream<String> problemsIn(String entry) {
        return Stream.of(
            matchable(entry) ? "" : problem(
                entry, "separate packages with / rather than ., because the "
                    + "coverage tool matches the VM name of a class and a pattern written with dots matches "
                    + "nothing"
            ),
            roleShaped(entry) ? "" : problem(
                entry, "exclude by file role such as **/*Mojo*, never by "
                    + "naming a class"
            )
        ).filter(problem -> !problem.isEmpty());
    }

    private static boolean namesARole(String segment) {
        return segment.contains(ROLE) && !ROLE.equals(segment) && !LOCATION.equals(segment);
    }

    private static String problem(String entry, String requirement) {
        return "Rewrite child coverage exclusion \"" + entry + "\"; " + requirement;
    }
}
