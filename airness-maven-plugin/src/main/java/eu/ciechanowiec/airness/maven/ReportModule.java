package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ReportAdvisory;
import eu.ciechanowiec.airness.governance.ReportImagePackage;
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
 * @param repairs    what the module does about the open findings its shipped images hold
 * @param images     every package the module's shipped images hold
 */
record ReportModule(
    String coordinate, List<ReportPackage> packages, List<ReportAdvisory> advisories,
    Map<String, String> evidence, List<String> repairs, List<ReportImagePackage> images
) {

    ReportModule {
        packages = List.copyOf(packages);
        advisories = List.copyOf(advisories);
        evidence = Map.copyOf(evidence);
        repairs = List.copyOf(repairs);
        images = List.copyOf(images);
    }

    /**
     * Evidence of a module that ships no image needing a repair.
     *
     * @param coordinate module identity
     * @param packages   resolved dependencies and build tools
     * @param advisories findings from that module's scanner
     * @param evidence   exact per-module SBOM and scanner provenance
     */
    ReportModule(
        String coordinate, List<ReportPackage> packages, List<ReportAdvisory> advisories, Map<String, String> evidence
    ) {
        this(coordinate, packages, advisories, evidence, List.of(), List.of());
    }
}
