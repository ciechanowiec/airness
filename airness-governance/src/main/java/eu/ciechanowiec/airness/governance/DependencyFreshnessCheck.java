package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * No declared dependency, plugin, or parent trails the latest stable release by the failing bound that
 * {@link DependencyFreshnessRules} states. Calendar versions beginning with {@code 20**} compare their
 * leading year, and other schemes with no comparable level are skipped.
 *
 * <p>The registry read can fail, and when it does the check fails with it. A dependency whose latest
 * release could not be read is not a dependency known to be current, so passing on missing data would
 * turn an outage into a green build, which is the one outcome this check must not have.
 *
 * <p>The bound stops short of a newest release the blocklist refuses. A coordinate that turned non-open
 * at a known release is still measured, but never past the last release a project may declare, because
 * failing a project for not upgrading into a release another check would then refuse would leave it no
 * green state at all. The update report still lists the release, with a note saying why the bound
 * passed it by.
 */
public final class DependencyFreshnessCheck {

    private static final String HEADLINE = "Dependencies, plugins, and parents trailing by the major-version bound";

    private final int scanned;
    private final List<VersionUpdate> updates;

    /**
     * Reads and evaluates the declared dependencies in one pom.
     *
     * @param pom      the POM whose declared dependencies are read
     * @param registry the base URL of the registry to ask
     */
    public DependencyFreshnessCheck(Path pom, String registry) {
        this(
            DeclaredCoordinates.from(pom).stream()
                .map(coordinate -> new OwnedCoordinate(pom.getFileName().toString(), coordinate))
                .toList(),
            registry
        );
    }

    /**
     * Uses coordinates whose versions have already been resolved.
     *
     * @param coordinates directly declared dependencies, plugins, and parents with their owners
     * @param registry    registry base URL
     */
    public DependencyFreshnessCheck(Collection<OwnedCoordinate> coordinates, String registry) {
        List<OwnedCoordinate> checkable = coordinates.stream()
            .filter(MavenCentral::checkable)
            .distinct()
            .toList();
        this.scanned = checkable.size();
        this.updates = checkable.stream()
            .map(coordinate -> MavenCentral.update(registry, coordinate))
            .flatMap(Optional::stream)
            .toList();
    }

    /**
     * How many stable declarations are asked about, which a caller logs so the reach of a clean verdict
     * is on the record.
     *
     * @return the number of dependencies in scope
     */
    public int scanned() {
        return this.scanned;
    }

    /**
     * Every available stable update, including minor and patch releases that remain within the failing
     * freshness bound.
     *
     * @return available updates in declaration order
     */
    public List<VersionUpdate> updates() {
        return List.copyOf(this.updates);
    }

    /**
     * Every newest release the blocklist refuses, which the bound does not demand, one note each.
     *
     * @return the notes, in declaration order
     */
    public List<String> refusedLatest() {
        return this.updates.stream().map(DependencyFreshnessCheck::refusedNote).flatMap(Optional::stream).toList();
    }

    /**
     * Every dependency trailing by the bound, one per entry.
     *
     * @return the single verdict this check produces
     */
    public List<Findings> findings() {
        return List.of(
            new Findings(
                HEADLINE,
                this.updates.stream()
                    .filter(update -> refusedNote(update).isEmpty())
                    .map(DependencyFreshnessRules::violation)
                    .flatMap(Optional::stream)
                    .toList()
            )
        );
    }

    private static Optional<String> refusedNote(VersionUpdate update) {
        DeclaredCoordinate declared = update.declared().coordinate();
        DeclaredCoordinate newest = new DeclaredCoordinate(declared.groupId(), declared.artifactId(), update.latest());
        return Blocklist.coordinate(newest).map(
            refusal -> "[%s] %s:%s: the newest release %s is refused (%s), so freshness stops short of it".formatted(
                update.declared().owner(), declared.groupId(), declared.artifactId(), update.latest(), refusal.reason()
            )
        );
    }
}
