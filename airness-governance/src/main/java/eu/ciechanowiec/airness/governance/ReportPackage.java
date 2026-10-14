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
 * @param notices    archive notices and explicitly attributed, verified upstream license evidence
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
     * Whether a module reaches this artifact on its production classpath.
     *
     * @return whether production notices must be collected
     */
    public boolean production() {
        return this.uses.stream().anyMatch(use -> List.of("compile", "runtime").contains(use.scope()));
    }

    /**
     * Returns only notices physically shipped in the artifact.
     *
     * @return archive entry names and their texts
     */
    public Map<String, String> archiveNotices() {
        return this.notices.entrySet().stream().filter(entry -> !entry.getKey().startsWith(LICENSE_EVIDENCE_PREFIX))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
