package eu.ciechanowiec.airness.governance;

import java.util.Set;

/**
 * The SPDX license list a project's own license is named from: every identifier the list still recommends, and
 * the version of the list, which a refusal names so a reader can tell a stale list from a misspelt license.
 *
 * <p>Held as a record so that the rule reading it stays in {@code airness-governance}, which reads no JSON. The
 * plugin loads the list and passes it in.
 *
 * @param version     the version of the SPDX license list, such as {@code 3.29.0}
 * @param identifiers every identifier the list does not mark deprecated, spelt as the list spells it
 */
public record LicenseCatalogue(String version, Set<String> identifiers) {

    /**
     * Makes a defensive copy so a caller cannot change the list after handing it over.
     *
     * @param version     the version of the SPDX license list
     * @param identifiers every identifier the list does not mark deprecated
     */
    public LicenseCatalogue {
        identifiers = Set.copyOf(identifiers);
    }
}
