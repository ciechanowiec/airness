package eu.ciechanowiec.airness.governance;

import java.util.Arrays;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * The classes a project leaves out of the per-class coverage floor, each with the reason it gives.
 *
 * <p>{@value #PROPERTY} holds entries separated by semicolons, each a qualified class pattern, then {@code --},
 * then the reason the class's role gives for leaving it out, as in
 * {@code com.example.Application -- started only by the launcher, which the browser suite drives}. A semicolon
 * separates the entries rather than a comma, because a reason is prose and prose has commas in it.
 *
 * <p>An exclusion switches the coverage rule off for a whole class, as a suppression switches a rule off where it
 * stands, so each one carries its reason as a suppression does and counts against the same ceiling. JaCoCo reads
 * the patterns alone, which the harness derives into {@value #PATTERNS} before the coverage check runs.
 */
@UtilityClass
public final class CoverageExclusions {

    /**
     * The project setting that declares the exclusions with their reasons.
     */
    public static final String PROPERTY = "airness.coverage.excluded.classes";

    /**
     * The derived setting that hands JaCoCo the patterns alone, comma-separated.
     */
    public static final String PATTERNS = "airness.coverage.excluded.patterns";

    private static final String SEPARATOR = ";";
    private static final String REASON = "--";
    private static final String PATTERN_SEPARATOR = ",";
    private static final String EXAMPLE
        = "com.example.Application -- started only by the launcher the browser suite drives";

    /**
     * Every declared entry, pattern and reason together.
     *
     * @param declared the raw setting, empty when nothing is declared
     * @return the entries, stripped and without empty segments
     */
    public static List<String> entries(String declared) {
        return Arrays.stream(declared.split(SEPARATOR, -1)).map(String::strip).filter(entry -> !entry.isEmpty())
            .toList();
    }

    /**
     * The class pattern of every entry that names one.
     *
     * @param declared the raw setting, empty when nothing is declared
     * @return the patterns, in declaration order
     */
    public static List<String> patterns(String declared) {
        return entries(declared).stream().map(CoverageExclusions::pattern).filter(pattern -> !pattern.isEmpty())
            .toList();
    }

    /**
     * The patterns as JaCoCo reads them.
     *
     * @param declared the raw setting, empty when nothing is declared
     * @return the patterns, comma-separated
     */
    public static String jacocoPatterns(String declared) {
        return String.join(PATTERN_SEPARATOR, patterns(declared));
    }

    /**
     * One problem per entry that names no pattern, or names one but no reason for it.
     *
     * @param declared the raw setting, empty when nothing is declared
     * @return the problems, in declaration order
     */
    public static List<String> unreasoned(String declared) {
        return entries(declared).stream()
            .filter(entry -> pattern(entry).isEmpty() || reason(entry).isEmpty())
            .map(
                entry -> "\"%s\" needs a class pattern, then --, then the reason its role gives, as in %s"
                    .formatted(entry, EXAMPLE)
            ).toList();
    }

    private static String pattern(String entry) {
        int reason = entry.indexOf(REASON);
        return (reason >= 0 ? entry.substring(0, reason) : entry).strip();
    }

    private static String reason(String entry) {
        int reason = entry.indexOf(REASON);
        return reason >= 0 ? entry.substring(reason + REASON.length()).strip() : "";
    }
}
