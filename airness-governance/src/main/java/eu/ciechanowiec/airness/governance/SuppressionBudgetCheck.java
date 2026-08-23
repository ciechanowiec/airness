package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * The repository holds no more suppressions than the ceiling it declares.
 *
 * <p>Every other rule about a suppression asks whether this one is well formed: that it names a rule,
 * that it sits at the narrowest scope, that it still suppresses something, and that it carries a
 * reason. Whoever adds the suppression writes the reason, so none of those rules is ever the thing that
 * stops a suppression from being added. A ceiling is, because it makes the next one cost the removal of
 * an existing one, and that is the only pressure in the harness that acts on the total rather than on
 * the entry.
 *
 * <p>The ceiling scales with the code rather than being a flat number. A flat number strangles a small
 * repository and dissolves in a large one, and the same figure would mean two different things in the
 * two. A smallest ceiling holds underneath the rate so that an early project is not left with a budget
 * of one, and a declared rate of zero switches that floor off, which is how a repository states that it
 * carries no suppressions at all.
 *
 * <p>The offences are listed only once the ceiling is passed. Below it the count is a number the build
 * reports rather than a finding, and printing every suppression on every clean build would bury the run
 * that matters under the ones that do not.
 */
public final class SuppressionBudgetCheck {

    private static final int PER = 1000;
    private static final int SMALLEST_CEILING = 5;

    private final Path root;
    private final List<String> suppressions;
    private final int sources;
    private final int lines;
    private final double rate;

    /**
     * Reads the sources, locating every suppression and measuring the code it is weighed against.
     *
     * @param root        the working tree root
     * @param sourceRoots the directories whose Java sources are read
     * @param rate        how many suppressions are allowed per thousand lines, or zero to allow none
     */
    public SuppressionBudgetCheck(Path root, Collection<Path> sourceRoots, double rate) {
        List<Path> found = JavaSources.under(root, sourceRoots);
        this.root = root;
        this.rate = rate;
        this.sources = found.size();
        this.lines = found.stream().mapToInt(SuppressionBudgetCheck::lineCount).sum();
        this.suppressions = found.stream().flatMap(this::located).toList();
    }

    /**
     * How many sources the check read, which a caller refuses when it is zero.
     *
     * @return the number of Java sources in scope
     */
    public int scanned() {
        return this.sources;
    }

    /**
     * How many rules the sources suppress in total.
     *
     * @return the count in force
     */
    public int count() {
        return this.suppressions.size();
    }

    /**
     * The highest count the declared rate allows over the code that was read.
     *
     * @return the ceiling
     */
    public int ceiling() {
        return this.rate <= 0 ? 0 : Math.max(SMALLEST_CEILING, Math.toIntExact(Math.round(this.scaled())));
    }

    /**
     * The one rule, with one offence per suppression once the ceiling is passed and none below it.
     *
     * @return the verdict
     */
    public List<Findings> findings() {
        List<String> offences = this.count() > this.ceiling() ? this.suppressions : List.of();
        return List.of(new Findings(this.headline(), offences));
    }

    private double scaled() {
        return this.rate * this.lines / PER;
    }

    private String headline() {
        return "Suppressions passed the declared ceiling of %d for %d line(s) of source at %s per %d lines"
            .formatted(this.ceiling(), this.lines, this.rate, PER);
    }

    private Stream<String> located(Path source) {
        return Repository.readText(source).stream()
            .flatMap(text -> Suppressions.in(text).stream())
            .map(entry -> "%s: %s".formatted(this.root.relativize(source), entry));
    }

    private static int lineCount(Path source) {
        return Repository.readText(source)
            .map(text -> Math.toIntExact(text.lines().count()))
            .orElse(0);
    }
}
