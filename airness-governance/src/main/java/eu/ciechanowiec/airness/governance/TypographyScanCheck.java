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
     * @param excludedPrefixes repository-relative path prefixes to leave unread
     */
    public TypographyScanCheck(Path root, Collection<String> excludedPrefixes) {
        this.scan = TypographyScanner.scan(root, excludedPrefixes);
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
