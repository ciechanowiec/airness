package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * No declared dependency or plugin trails the latest stable release by the failing bound that
 * {@link DependencyFreshnessRules} states. Calendar versions beginning with {@code 20**} compare their
 * leading year, and other schemes with no comparable level are skipped.
 *
 * <p>The registry read can fail, and when it does the check fails with it. A dependency whose latest
 * release could not be read is not a dependency known to be current, so passing on missing data would
 * turn an outage into a green build, which is the one outcome this check must not have.
 */
public final class DependencyFreshnessCheck {

    private static final String HEADLINE = "Dependencies and plugins trailing by the major-version bound";

    private final String registry;
    private final List<DeclaredCoordinate> coordinates;

    /**
     * Reads the declared dependencies, without yet asking the registry about any of them.
     *
     * @param pom      the POM whose declared dependencies are read
     * @param registry the base URL of the registry to ask
     */
    public DependencyFreshnessCheck(Path pom, String registry) {
        this(DeclaredCoordinates.from(pom), registry);
    }

    /**
     * Uses coordinates whose versions have already been resolved.
     *
     * @param coordinates directly declared dependencies and plugins
     * @param registry    registry base URL
     */
    public DependencyFreshnessCheck(Collection<DeclaredCoordinate> coordinates, String registry) {
        this.registry = registry;
        this.coordinates = coordinates.stream()
            .filter(coordinate -> DependencyFreshnessRules.hasComparableMajor(coordinate.version()))
            .distinct()
            .toList();
    }

    /**
     * How many dependencies carry a comparable level and are therefore asked about, which a caller logs
     * so the reach of a clean verdict is on the record.
     *
     * @return the number of dependencies in scope
     */
    public int scanned() {
        return this.coordinates.size();
    }

    /**
     * Every dependency trailing by the bound, one per entry.
     *
     * @return the single verdict this check produces
     */
    public List<Findings> findings() {
        return List.of(
            new Findings(HEADLINE, this.coordinates.stream().flatMap(this::violation).toList())
        );
    }

    private Stream<String> violation(DeclaredCoordinate coordinate) {
        Optional<String> reported = DependencyFreshnessRules.violation(
            coordinate, MavenCentral.latestMajor(this.registry, coordinate)
        );
        return reported.stream();
    }
}
