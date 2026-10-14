package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ReportAdvisory;
import eu.ciechanowiec.airness.governance.ReportPackage;
import java.util.List;
import java.util.Map;

/**
 * Evidence collected after one module has completed its package phase.
 *
 * @param coordinate module identity
 * @param packages   resolved dependencies and build tools
 * @param advisories findings from that module's scanner
 * @param evidence   exact per-module SBOM and scanner provenance
 */
record ReportModule(
    String coordinate, List<ReportPackage> packages, List<ReportAdvisory> advisories,
    Map<String, String> evidence
) {

    ReportModule {
        packages = List.copyOf(packages);
        advisories = List.copyOf(advisories);
        evidence = Map.copyOf(evidence);
    }
}
