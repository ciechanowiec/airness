package eu.ciechanowiec.airness.governance;

import java.util.List;

/**
 * Who a project belongs to and on what terms, as its pom states it.
 *
 * @param licenses     the name of each license, in the order the pom lists them
 * @param developers   the name of each developer, empty for a developer the pom names only by id or email
 * @param organization the name of the organization, empty when the pom states none
 */
public record ProjectOwnership(List<String> licenses, List<String> developers, String organization) {

    /**
     * Makes defensive copies so a caller cannot change the lists after handing them over.
     *
     * @param licenses     the name of each license
     * @param developers   the name of each developer
     * @param organization the name of the organization
     */
    public ProjectOwnership {
        licenses = List.copyOf(licenses);
        developers = List.copyOf(developers);
    }
}
