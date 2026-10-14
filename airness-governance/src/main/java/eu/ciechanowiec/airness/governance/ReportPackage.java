package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Map;

/**
 * A resolved artifact and the evidence read from its published metadata and archive.
 *
 * @param coordinate complete Maven artifact coordinate
 * @param licenses   declared licenses, before policy aliases are applied
 * @param uses       every module and scope that reaches this version
 * @param notices    textual license and notice entries shipped in the artifact
 */
public record ReportPackage(
    String coordinate, List<String> licenses, List<ReportUse> uses,
    Map<String, String> notices
) {

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
}
