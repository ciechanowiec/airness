package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * The node projects hold no more suppressions than the ceiling their own code earns.
 *
 * <p>The ceiling is the one the node gates held before the harness had stack layers: two suppressions before
 * any code is written, and one more for every five hundred non-blank lines of first-party source. The caller
 * decides what first-party source is: the source roots of each node project, without tests, generated files
 * or the files the harness pins, so a ceiling is never earned by code the project did not write. It is a
 * verdict of its own beside the Java ceiling, because the two rates differ and neither may loosen the other.
 */
public final class NodeSuppressionCeiling {

    private static final int BASE_ALLOWANCE = 2;
    private static final int LINES_PER_SUPPRESSION = 500;

    private final List<String> suppressions;
    private final int sources;
    private final int lines;

    /**
     * Reads the sources, locating every suppression and measuring the code it is weighed against.
     *
     * @param root    the working tree root
     * @param sources the first-party JavaScript, TypeScript and stylesheet sources of the node projects
     */
    public NodeSuppressionCeiling(Path root, Collection<Path> sources) {
        List<Path> read = sources.stream().filter(path -> NodeSuppressions.readable(path.toString())).distinct()
            .toList();
        this.sources = read.size();
        this.lines = read.stream().mapToInt(NodeSuppressionCeiling::nonBlankLines).sum();
        this.suppressions = read.stream().flatMap(
            source -> Repository.readText(source).stream().flatMap(text -> NodeSuppressions.in(text).stream())
                .map(entry -> "%s: %s".formatted(root.relativize(source), entry))
        ).toList();
    }

    /**
     * How many sources the check read.
     *
     * @return the number of node sources in scope
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
     * The highest count the code earns.
     *
     * @return two, and one more per five hundred non-blank lines
     */
    public int ceiling() {
        return BASE_ALLOWANCE + this.lines / LINES_PER_SUPPRESSION;
    }

    /**
     * The one rule, with one offence per suppression once the ceiling is passed and none below it.
     *
     * @return the verdict
     */
    public List<Findings> findings() {
        List<String> offences = this.count() > this.ceiling() ? this.suppressions : List.of();
        return List.of(
            new Findings(
                "Node suppressions passed the ceiling of %d for %d non-blank line(s) of node source (two, and one per"
                    .formatted(this.ceiling(), this.lines) + " 500 lines)",
                offences
            )
        );
    }

    private static int nonBlankLines(Path source) {
        return Repository.readText(source)
            .map(text -> Math.toIntExact(text.lines().filter(line -> !line.isBlank()).count()))
            .orElse(0);
    }
}
