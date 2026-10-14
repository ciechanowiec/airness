package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.stream.Stream;

/**
 * License and vulnerability evidence for all participating modules.
 *
 * @param packages   resolved artifact versions
 * @param advisories all scanner findings, including suppressed findings
 * @param policy     the license policy the build enforces
 */
public record ReportInventory(
    List<ReportPackage> packages, List<ReportAdvisory> advisories,
    ReportLicensePolicy policy
) {

    public ReportInventory {
        packages = List.copyOf(packages);
        advisories = List.copyOf(advisories);
    }

    /**
     * Whether every dependency satisfies the license and vulnerability policies.
     *
     * @return the dependency policy verdict, independent of source cleanliness
     */
    public boolean compliant() {
        return this.failures().isEmpty();
    }

    /**
     * Names the exact evidence that prevents a dependency-policy pass.
     *
     * @return license refusals and unresolved advisories
     */
    public List<String> failures() {
        return Stream.concat(
            this.packages.stream().filter(this.policy::refuses)
                .map(entry -> "License outside inherited policy: " + entry.coordinate() + " " + entry.licenses()),
            this.advisories.stream().filter(advisory -> "Unresolved".equals(advisory.status()))
                .map(advisory -> "Unresolved vulnerability: " + advisory.identity() + " " + advisory.evidence())
        ).toList();
    }
}
