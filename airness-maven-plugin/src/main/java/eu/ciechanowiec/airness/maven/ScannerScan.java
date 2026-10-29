package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import java.io.IOException;
import java.util.List;

/**
 * One scan over a {@link ScannerTree}, which may fail outright rather than report a finding.
 */
@FunctionalInterface
interface ScannerScan {

    /**
     * Runs the scan.
     *
     * @return the verdict of each scanner the scan ran
     * @throws IOException when the scan could not run
     */
    List<Findings> verdicts() throws IOException;
}
