package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * License and vulnerability evidence for all participating modules.
 *
 * @param packages   resolved artifact versions
 * @param advisories all scanner findings, including mitigated findings
 * @param policy     the license policy the build enforces
 */
public record ReportInventory(
    List<ReportPackage> packages, List<ReportAdvisory> advisories,
    ReportLicensePolicy policy
) {

    private static final Pattern PACKAGE_URL = Pattern.compile("^pkg:maven/([^/]+)/([^@]+)@([^?]+)");
    private static final String WEAK = "Weak copyleft";

    public ReportInventory {
        packages = List.copyOf(packages);
        advisories = List.copyOf(advisories.stream().sorted(ReportAdvisory.TRIAGE).toList());
    }

    /**
     * Whether every license is within the policy and no advisory is open.
     *
     * @return the dependency policy verdict, independent of source cleanliness
     */
    public boolean withinPolicy() {
        return this.failures().isEmpty();
    }

    /**
     * Names the exact evidence that prevents a dependency-policy pass.
     *
     * @return license refusals and open advisories
     */
    public List<String> failures() {
        return Stream.concat(
            this.refused().stream()
                .map(entry -> "License outside inherited policy: " + entry.coordinate() + " " + entry.licenses()),
            this.open().stream()
                .map(advisory -> "Open vulnerability: " + advisory.identity() + " in " + advisory.artifact())
        ).toList();
    }

    /**
     * The advisories no recorded mitigation covers.
     *
     * @return open advisories, most severe first
     */
    public List<ReportAdvisory> open() {
        return this.advisories.stream().filter(ReportAdvisory::open).toList();
    }

    /**
     * The advisories a recorded mitigation covers.
     *
     * @return mitigated advisories, most severe first
     */
    public List<ReportAdvisory> mitigated() {
        return this.advisories.stream().filter(advisory -> !advisory.open()).toList();
    }

    /**
     * The artifact versions whose every admitted license carries a weak copyleft condition.
     *
     * @return weak copyleft packages
     */
    public List<ReportPackage> weakCopyleft() {
        return this.packages.stream().filter(entry -> WEAK.equals(this.policy.classification(entry.licenses())))
            .toList();
    }

    /**
     * The artifact versions no declared license of which the policy admits.
     *
     * @return refused packages
     */
    public List<ReportPackage> refused() {
        return this.packages.stream().filter(this.policy::refuses).toList();
    }

    /**
     * Whether the advisory's artifact is on the production classpath of any module.
     *
     * @param advisory a reported advisory
     * @return whether a production artifact version carries it
     */
    public boolean production(ReportAdvisory advisory) {
        Matcher address = PACKAGE_URL.matcher(advisory.artifact());
        if (!address.find()) {
            return false;
        }
        String prefix = address.group(1) + ':' + address.group(2) + ':';
        String suffix = ':' + address.group(3);
        return this.packages.stream().filter(ReportPackage::production).map(ReportPackage::coordinate)
            .anyMatch(coordinate -> coordinate.startsWith(prefix) && coordinate.endsWith(suffix));
    }
}
