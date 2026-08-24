package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The classes a coverage report measured, read so that an exclusion naming none of them can be found.
 *
 * <p>An exclusion that reaches nothing is the quietest way a project can be wrong about its own floor.
 * The setting is accepted, it reads as though it lifted something out, and the coverage tool has nothing
 * to say because from its side there is simply a pattern that never matched. The report goes on looking
 * exactly as it would have looked with no exclusion at all, which is indistinguishable from the setting
 * working. Every other exception the harness holds is checked for still applying, and this one now is
 * too: the typography scan rejects a prefix that excluded nothing, and the vulnerability scan rejects a
 * rule that suppressed nothing.
 *
 * <p>The name a pattern is matched against is the qualified name of the class, with dots, and a nested
 * class is reached through its outer one. The report writes both differently, with slashes between
 * packages and a dollar sign before a nested name, so each class is offered here under both spellings of
 * its nesting. Matching either counts as reaching it, which is deliberate: the cost of missing a dead
 * pattern is a finding nobody gets, and the cost of inventing one is a build that fails over a setting
 * that works.
 */
public final class CoverageReport {

    private static final Pattern DECLARATION = Pattern.compile("<class name=\"([^\"]+)\"");
    private static final char PACKAGE_SEPARATOR = '/';
    private static final char NESTING_SEPARATOR = '$';
    private static final char SOURCE_SEPARATOR = '.';

    private final Set<String> measured;

    /**
     * Reads one coverage report.
     *
     * @param report the report the coverage tool wrote
     */
    public CoverageReport(Path report) {
        this.measured = namesIn(read(report));
    }

    /**
     * How many classes the report measured, so a caller can tell an empty report from a clean one.
     *
     * @return the number of distinct class names read
     */
    public int measured() {
        return this.measured.size();
    }

    /**
     * The declared exclusions that name nothing the report measured.
     *
     * @param patterns the declared exclusion patterns
     * @return one entry per pattern that reached no class, in the order the patterns are written
     */
    public List<String> unreached(Collection<String> patterns) {
        return patterns.stream().filter(pattern -> !this.reaches(pattern)).toList();
    }

    private boolean reaches(CharSequence pattern) {
        Pattern expression = glob(pattern);
        return this.measured.stream().anyMatch(name -> expression.matcher(name).matches());
    }

    /**
     * The coverage tool reads a pattern as a glob rather than as a regular expression, where a star
     * stands for any run of characters and a question mark for one. Everything else is a literal, which
     * is what keeps a dollar sign in a nested name from being read as the end of the input.
     */
    private static Pattern glob(CharSequence pattern) {
        String regex = pattern.chars()
            .mapToObj(symbol -> expression((char) symbol))
            .collect(Collectors.joining());
        return Pattern.compile(regex);
    }

    private static String expression(char symbol) {
        return switch (symbol) {
            case '*' -> ".*";
            case '?' -> ".";
            default -> Pattern.quote(String.valueOf(symbol));
        };
    }

    private static Set<String> namesIn(CharSequence report) {
        return DECLARATION.matcher(report).results()
            .map(hit -> hit.group(1))
            .map(name -> name.replace(PACKAGE_SEPARATOR, SOURCE_SEPARATOR))
            .flatMap(name -> Stream.of(name, name.replace(NESTING_SEPARATOR, SOURCE_SEPARATOR)))
            .collect(Collectors.toUnmodifiableSet());
    }

    private static String read(Path report) {
        try {
            return Files.readString(report);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the coverage report at " + report, exception);
        }
    }
}
