package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The inherited license check's allowlist and spelling merges, read rather than duplicated.
 *
 * @param allowed identifiers admitted by the check
 * @param aliases declared spelling to admitted identifier
 */
public record ReportLicensePolicy(Set<String> allowed, Map<String, String> aliases) {

    private static final String REFUSED = "Outside policy";
    private static final String WEAK = "Weak copyleft";
    private static final String PERMISSIVE = "Permissive";

    public ReportLicensePolicy {
        allowed = Set.copyOf(allowed);
        aliases = Map.copyOf(aliases);
        if (allowed.isEmpty()) {
            throw new IllegalArgumentException("The inherited license allowlist is empty");
        }
    }

    /**
     * Normalizes a spelling exactly as the license check's merges declare.
     *
     * @param declared supplier's license declaration
     * @return policy identifier, or the unchanged declaration
     */
    public String normalized(String declared) {
        return this.aliases.getOrDefault(declared, declared);
    }

    /**
     * Classifies all declared alternatives using the check's admitted-license semantics.
     *
     * @param licenses supplier declarations
     * @return readable license class
     */
    public String classification(List<String> licenses) {
        List<String> admitted = licenses.stream().map(this::normalized).filter(this.allowed::contains).toList();
        if (admitted.isEmpty()) {
            return REFUSED;
        }
        return admitted.stream().allMatch(ReportLicensePolicy::copyleft) ? WEAK : PERMISSIVE;
    }

    /**
     * Whether the dependency violates the inherited allowlist.
     *
     * @param entry resolved artifact
     * @return whether no declared alternative is admitted
     */
    public boolean refuses(ReportPackage entry) {
        return REFUSED.equals(this.classification(entry.licenses()));
    }

    private static boolean copyleft(String license) {
        return Stream.of("LGPL-", "MPL-", "EPL-", "CPL-", "CDDL-", "GPL-")
            .anyMatch(license::startsWith);
    }
}
