package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A resolved artifact and the evidence read from its published metadata and archive.
 *
 * @param coordinate complete Maven artifact coordinate
 * @param licenses   declared licenses, before policy aliases are applied
 * @param uses       every module and scope that reaches this version
 * @param notices    license files the package ships, and explicitly attributed, verified upstream license evidence
 */
public record ReportPackage(
    String coordinate, List<String> licenses, List<ReportUse> uses,
    Map<String, String> notices
) {

    /**
     * Distinguishes retained upstream evidence from files actually shipped in an artifact.
     */
    public static final String LICENSE_EVIDENCE_PREFIX = "Airness license evidence: ";

    public ReportPackage {
        licenses = List.copyOf(licenses);
        uses = List.copyOf(uses);
        notices = Map.copyOf(notices);
    }

    /**
     * Names the declarations for a reader, including an explicit absence of supplier metadata.
     *
     * @return declared alternatives or a missing-declaration label
     */
    public String licenseSummary() {
        String declared = String.join(" OR ", this.licenses);
        return declared.isBlank() ? "Not declared" : declared;
    }

    /**
     * The licenses as the policy names them, each declared alternative normalized by the aliases the license check
     * declares, so one license reads the same whatever spelling its supplier chose.
     *
     * @param policy the license policy
     * @return the normalized alternatives joined by {@code OR}, or a missing-declaration label
     */
    public String licenseIds(ReportLicensePolicy policy) {
        String normalized = this.licenses.stream().map(policy::display).distinct().collect(Collectors.joining(" OR "));
        return normalized.isBlank() ? "Not declared" : normalized;
    }

    /**
     * Whether a module reaches this artifact in what the product ships.
     *
     * @return whether its license files are reproduced as third-party notices
     */
    public boolean production() {
        return this.uses.stream().anyMatch(ReportUse::production);
    }

    /**
     * Returns only the license files the package itself ships.
     *
     * @return archive entry names and their texts
     */
    public Map<String, String> archiveNotices() {
        return this.notices.entrySet().stream().filter(entry -> !entry.getKey().startsWith(LICENSE_EVIDENCE_PREFIX))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
