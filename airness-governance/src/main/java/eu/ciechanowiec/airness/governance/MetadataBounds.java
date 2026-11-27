package eu.ciechanowiec.airness.governance;

import java.time.Year;
import java.util.List;
import java.util.Optional;

/**
 * What the root pom's metadata is judged against, beside the pom itself.
 *
 * @param licenses    the SPDX license list a license is named from
 * @param current     the year of the build, which no inception year may follow
 * @param firstCommit the year of the repository's first commit, which no inception year may follow, or nothing
 *                    when the history is unborn or truncated and the first commit cannot be read
 * @param harness     the metadata each Airness pom up the parent chain declares itself, which the project's own
 *                    may not copy
 */
public record MetadataBounds(
    LicenseCatalogue licenses, Year current, Optional<Year> firstCommit, List<ProjectMetadata> harness
) {

    /**
     * Makes a defensive copy so a caller cannot change the bounds after handing them over.
     *
     * @param licenses    the SPDX license list
     * @param current     the year of the build
     * @param firstCommit the year of the repository's first commit, when it can be read
     * @param harness     the metadata each Airness pom up the parent chain declares itself
     */
    public MetadataBounds {
        harness = List.copyOf(harness);
    }
}
