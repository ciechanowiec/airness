package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * The repository holds no more suppressions than the ceiling its Java source earns.
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
 * two. A smallest ceiling holds underneath the rate so that an early project is not left with a ceiling
 * of one.
 *
 * <p>The offences are listed only once the ceiling is passed. Below it the count is a number the build
 * reports rather than a finding, and printing every suppression on every clean build would bury the run
 * that matters under the ones that do not.
 */
public final class JavaSuppressionCeilingCheck {

    private static final int PER = 1000;
    private static final int SMALLEST_CEILING = 5;

    /*
     * Three suppressions per thousand lines of Java source, with the smallest ceiling of five
     * holding underneath the rate. The rate leaves room for justified analyzer boundaries that code cannot
     * remove while keeping suppressions sparse as a project grows.
     *
     * Deliberately not a parameter. A ceiling a project can raise is a ceiling that gets raised on the change
     * that would otherwise have failed, which is the one moment the ceiling exists to catch.
     */
    private static final double RATE = 3;

    private final Path root;
    private final List<String> suppressions;
    private final int sources;
    private final int lines;

    /**
     * Reads the sources, locating every suppression and measuring the code it is weighed against.
     *
     * @param root        the working tree root
     * @param sourceRoots the directories whose Java sources are read
     */
    public JavaSuppressionCeilingCheck(Path root, Collection<Path> sourceRoots) {
        List<Path> found = JavaSources.under(root, sourceRoots);
        this.root = root;
        this.sources = found.size();
        this.lines = found.stream().mapToInt(JavaSuppressionCeilingCheck::lineCount).sum();
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
     * The highest count the rate allows over the code that was read.
     *
     * @return the ceiling
     */
    public int ceiling() {
        return Math.max(SMALLEST_CEILING, Math.toIntExact(Math.round(this.scaled())));
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
        return RATE * this.lines / PER;
    }

    private String headline() {
        return "Suppressions passed the declared ceiling of %d for %d line(s) of source at %s per %d lines"
            .formatted(this.ceiling(), this.lines, RATE, PER);
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
