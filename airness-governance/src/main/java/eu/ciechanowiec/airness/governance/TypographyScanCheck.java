package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Every tracked text file, apart from the roles the caller declares exempt, uses only plain ASCII
 * typography.
 *
 * <p>A prefix that excluded nothing is reported alongside the violations. It names a directory that has
 * since moved or gone, and an exemption nobody can see expiring is one that outlives its reason, so the
 * list stays as short as the roles that actually need it.
 */
public final class TypographyScanCheck {

    private static final String VIOLATIONS = "Banned typography found (use the plain ASCII equivalents)";
    private static final String STALE = "An exclusion prefix excluded nothing, so it names a path that moved or went";

    private final TypographyScan scan;

    /**
     * Reads the tree once, so the violations and the skipped counts come from one pass.
     *
     * @param root             the working tree root
     * @param excludedPrefixes repository-relative path prefixes to leave unread, matched whole segment by
     *                         whole segment
     */
    public TypographyScanCheck(Path root, Collection<String> excludedPrefixes) {
        this(root, excludedPrefixes, List.of());
    }

    /**
     * Reads the tree once, leaving out the files a generator owns as well as the exempt prefixes.
     *
     * <p>A generated file carries whatever text its generator copies into it, such as the labels a framework
     * configuration declares, and the agent runtime is denied editing it, so a finding there could not be
     * repaired where it is reported. A generated glob that matches nothing is not stale: a layer declares the
     * files its stack may write, whether or not this repository has them yet.
     *
     * @param root             the working tree root
     * @param excludedPrefixes repository-relative path prefixes to leave unread, matched whole segment by
     *                         whole segment
     * @param generated        repository-relative globs of the files a generator owns
     */
    public TypographyScanCheck(Path root, Collection<String> excludedPrefixes, Collection<String> generated) {
        this.scan = TypographyScanner.scan(root, excludedPrefixes, generated);
    }

    /**
     * How many files the generated globs kept out of the scan.
     *
     * @return the count
     */
    public long generated() {
        return this.scan.generated();
    }

    /**
     * How many files each exclusion prefix kept out of the scan, which a caller logs so the reach of a
     * clean verdict is on the record rather than in someone's memory.
     *
     * @return the skipped count per prefix
     */
    public Map<String, Long> skipped() {
        return this.scan.skipped();
    }

    /**
     * How many files the check read, which a caller refuses when it is zero.
     *
     * @return the number of tracked files in scope
     */
    public int scanned() {
        return this.scan.scanned();
    }

    /**
     * The banned code points, and the exemptions that no longer exempt anything.
     *
     * @return one verdict per rule
     */
    public List<Findings> findings() {
        return List.of(
            new Findings(VIOLATIONS, this.scan.violations()),
            new Findings(STALE, this.stale())
        );
    }

    private List<String> stale() {
        return this.scan.skipped().entrySet().stream()
            .filter(entry -> entry.getValue() == 0)
            .map(Map.Entry::getKey)
            .sorted()
            .toList();
    }
}
