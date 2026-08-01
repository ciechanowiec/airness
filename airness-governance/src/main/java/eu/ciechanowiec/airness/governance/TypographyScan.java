package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Map;

/**
 * What a typography scan found, and what it never read.
 *
 * <p>The skipped counts travel with the violations rather than being logged where they are computed,
 * because a prefix that skipped nothing is itself a finding: it names a directory that has since moved
 * or gone, and an exemption nobody can see expiring is one that outlives its reason. A caller holding
 * both can say how much of the tree the clean verdict actually covers.
 *
 * @param violations every banned code point, one entry each, naming its file, line, and column
 * @param skipped    how many files each exclusion prefix kept out of the scan, keyed by that prefix
 */
record TypographyScan(List<String> violations, Map<String, Long> skipped) {

    /**
     * Copies both collections, so a caller that keeps its own cannot alter a scan already reported.
     *
     * @param violations every banned code point
     * @param skipped    how many files each exclusion prefix kept out
     */
    TypographyScan {
        violations = List.copyOf(violations);
        skipped = Map.copyOf(skipped);
    }
}
