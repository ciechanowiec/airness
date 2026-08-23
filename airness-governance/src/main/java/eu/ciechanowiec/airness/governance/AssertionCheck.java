package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Every test in the sources it reads asserts an observable outcome, and no assertion is settled before
 * the code under test runs.
 *
 * <p>The coverage floor answers which lines ran, and nothing else in the harness answers which
 * behaviours were judged. A suite can hold both coverage floors and still assert nothing, because a
 * line is covered by being executed rather than by being checked. This check separates the two
 * questions, so that a green run means the assertions held and that they were there at all.
 *
 * <p>Only test sources are read. The rules are about what a test proves, and a production method that
 * asserts nothing is an ordinary production method rather than a finding.
 */
public final class AssertionCheck {

    private static final String UNPROVEN
        = "A test reaches no assertion, so it can only fail by throwing and reports a pass either way";
    private static final String SETTLED
        = "An assertion over literals alone cannot fail, so no change to the code under test can move it";

    private final Path root;
    private final List<Path> sources;

    /**
     * Reads the sources once, so both rules are answered from one pass over the tree.
     *
     * @param root      the working tree root
     * @param testRoots the test source directories whose Java sources are read
     */
    public AssertionCheck(Path root, Collection<Path> testRoots) {
        this.root = root;
        this.sources = JavaSources.under(root, testRoots);
    }

    /**
     * How many sources the check read, which a caller refuses when it is zero.
     *
     * @return the number of Java test sources in scope
     */
    public int scanned() {
        return this.sources.size();
    }

    /**
     * Both rules, each reported separately so a failure names which one was broken.
     *
     * @return one verdict per rule
     */
    public List<Findings> findings() {
        return List.of(
            new Findings(UNPROVEN, this.offences(AssertionRules::unproven)),
            new Findings(SETTLED, this.offences(AssertionRules::settled))
        );
    }

    private List<String> offences(Function<CharSequence, List<String>> rule) {
        return this.sources.stream().flatMap(source -> this.offencesIn(source, rule)).toList();
    }

    private Stream<String> offencesIn(Path source, Function<CharSequence, List<String>> rule) {
        return Repository.readText(source).stream()
            .flatMap(text -> rule.apply(text).stream())
            .map(line -> "%s: %s".formatted(this.root.relativize(source), line));
    }
}
