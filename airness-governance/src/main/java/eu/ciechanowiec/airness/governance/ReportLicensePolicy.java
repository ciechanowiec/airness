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
     * <p>An alternative that joins licenses with {@code AND}, as an npm expression can, is admitted only
     * when every one of them is.
     *
     * @param licenses supplier declarations
     * @return readable license class
     */
    public String classification(List<String> licenses) {
        List<List<String>> admitted = licenses.stream().map(this::conjuncts)
            .filter(this.allowed::containsAll).toList();
        if (admitted.isEmpty()) {
            return REFUSED;
        }
        return admitted.stream().allMatch(conjuncts -> conjuncts.stream().anyMatch(ReportLicensePolicy::copyleft))
            ? WEAK : PERMISSIVE;
    }

    // One alternative is a license, or licenses joined as an npm expression joins them, every one of which the
    // allowlist must admit (LicenseExpression). A license name that itself contains AND, as the CDDL's long name
    // does, is one license, so an alternative the allowlist or its spellings admit whole is never split.
    private List<String> conjuncts(String alternative) {
        String whole = this.normalized(alternative);
        return this.allowed.contains(whole) ? List.of(whole)
            : Stream.of(alternative.split(LicenseExpression.CONJUNCTION, -1)).map(String::strip)
                .map(this::normalized).toList();
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

    /**
     * Whether an admitted license carries a file-level or library-level copyleft condition.
     *
     * @param license an admitted identifier
     * @return whether the license is admitted only for components used unmodified
     */
    static boolean copyleft(String license) {
        return Stream.of("LGPL-", "MPL-", "EPL-", "CPL-", "CDDL-", "GPL-")
            .anyMatch(license::startsWith);
    }
}
