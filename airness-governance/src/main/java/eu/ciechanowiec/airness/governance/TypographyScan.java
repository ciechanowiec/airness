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
 * <p>The scanned count answers the question the skipped counts cannot. A prefix that excludes nothing is
 * reported, while a set of prefixes that between them exclude everything leaves a scan with no violations
 * to report and no stale prefix to name, which reads exactly as a clean tree reads. The count is how many
 * files the scan opened, so a caller can refuse a verdict that covered none of them.
 *
 * @param violations every banned code point, one entry each, naming its file, line, and column
 * @param skipped    how many files each exclusion prefix kept out of the scan, keyed by that prefix
 * @param scanned    how many files the scan read, which is every tracked file no prefix exempted
 */
record TypographyScan(List<String> violations, Map<String, Long> skipped, int scanned) {

    /**
     * Copies both collections, so a caller that keeps its own cannot alter a scan already reported.
     *
     * @param violations every banned code point
     * @param skipped    how many files each exclusion prefix kept out
     * @param scanned    how many files the scan read
     */
    TypographyScan {
        violations = List.copyOf(violations);
        skipped = Map.copyOf(skipped);
    }
}
