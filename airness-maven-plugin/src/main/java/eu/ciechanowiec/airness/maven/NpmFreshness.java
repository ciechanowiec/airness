package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.DeclaredCoordinate;
import eu.ciechanowiec.airness.governance.DependencyFreshnessCheck;
import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.OwnedCoordinate;
import eu.ciechanowiec.airness.governance.VersionUpdate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.maven.artifact.versioning.ComparableVersion;

/**
 * The freshness bound over what the Maven registries do not list: the npm packages a node project depends on
 * directly, and the Node and pnpm the harness pins.
 *
 * <p>One bound holds for every ecosystem: a declaration fails when the latest stable release is two majors or
 * more ahead of it, and every newer stable release is reported. A version with a pre-release part is not
 * stable. A listing that cannot be read fails the bound for that declaration rather than passing it, which is
 * what the Maven half does when Maven Central does not answer.
 *
 * @param scanned  how many declarations were asked about
 * @param updates  every newer stable release found
 * @param trailing every declaration the bound fails
 * @param unread   every declaration whose listing could not be read, with the reason
 */
record NpmFreshness(int scanned, List<VersionUpdate> updates, List<String> trailing, List<String> unread) {

    private static final String HEADLINE = "npm packages and the Node toolchain trailing by the major-version bound";
    private static final String UNREAD = "Release listings that could not be read, so the freshness bound fails rather"
        + " than passing without them";

    /**
     * Copies every list, so the verdict cannot change after it was reached.
     *
     * @param scanned  the declarations asked about
     * @param updates  the newer stable releases
     * @param trailing the declarations the bound fails
     * @param unread   the unreadable listings
     */
    NpmFreshness {
        updates = List.copyOf(updates);
        trailing = List.copyOf(trailing);
        unread = List.copyOf(unread);
    }

    /**
     * Asks for the releases of every declaration.
     *
     * @param declared the declarations, each with the owner the report names
     * @param versions every version the declaration's listing names, throwing when it cannot be read
     * @return the result
     */
    static NpmFreshness of(Collection<OwnedCoordinate> declared, Function<OwnedCoordinate, List<String>> versions) {
        return of(declared, versions, DependencyFreshnessCheck::trailing);
    }

    /**
     * Asks for the releases of every declaration, holding each update to a bound of the caller's.
     *
     * @param declared the declarations, each with the owner the report names
     * @param versions every version the declaration's listing names, throwing when it cannot be read
     * @param bound    the violation an update is, or nothing when it is within the bound
     * @return the result
     */
    static NpmFreshness of(
        Collection<OwnedCoordinate> declared, Function<OwnedCoordinate, List<String>> versions,
        Function<VersionUpdate, Optional<String>> bound
    ) {
        List<Lookup> read = declared.stream().distinct().filter(entry -> stable(entry.coordinate().version()))
            .map(entry -> Lookup.of(entry, versions)).toList();
        List<VersionUpdate> updates = read.stream().flatMap(
            lookup -> lookup.latest().stream().map(latest -> new VersionUpdate(lookup.entry(), latest))
        ).filter(NpmFreshness::newer).toList();
        return new NpmFreshness(
            read.size(), updates, updates.stream().map(bound).flatMap(Optional::stream).toList(),
            read.stream().flatMap(lookup -> lookup.problem().stream()).toList()
        );
    }

    /**
     * The two verdicts: the declarations trailing by the bound, and the listings that could not be read.
     *
     * @return the verdicts
     */
    List<Findings> findings() {
        return List.of(
            new Findings(HEADLINE, this.trailing),
            new Findings(UNREAD, this.unread)
        );
    }

    /**
     * A declaration of an npm package, owned by the node project that declares it.
     *
     * @param owner   the node project, as the report names it
     * @param name    the package name
     * @param version the resolved version
     * @return the declaration
     */
    static OwnedCoordinate npm(String owner, String name, String version) {
        return new OwnedCoordinate(owner, new DeclaredCoordinate("npm", name, version));
    }

    private static boolean newer(VersionUpdate update) {
        return new ComparableVersion(update.latest())
            .compareTo(new ComparableVersion(update.declared().coordinate().version())) > 0;
    }

    private static boolean stable(String version) {
        return !version.isEmpty() && !version.contains("-") && Stream.of(version.split("\\.", -1))
            .allMatch(part -> !part.isEmpty() && part.chars().allMatch(Character::isDigit));
    }

    /**
     * One declaration's latest stable release, or why it could not be read.
     *
     * @param entry   the declaration
     * @param latest  the latest stable release, empty when the listing could not be read
     * @param problem why the listing could not be read, empty when it could
     */
    private record Lookup(OwnedCoordinate entry, Optional<String> latest, Optional<String> problem) {

        private static String latest(OwnedCoordinate entry, List<String> versions) {
            return versions.stream().filter(NpmFreshness::stable)
                .max(Comparator.comparing(ComparableVersion::new))
                .orElseThrow(
                    () -> new IllegalStateException("no stable release of " + entry.coordinate().artifactId())
                );
        }

        static Lookup of(OwnedCoordinate entry, Function<OwnedCoordinate, List<String>> versions) {
            try {
                return new Lookup(
                    entry, Optional.of(latest(entry, versions.apply(entry))), Optional.empty()
                );
            } catch (IllegalStateException exception) {
                return new Lookup(
                    entry, Optional.empty(),
                    Optional.of(
                        "[%s] %s: %s".formatted(entry.owner(), entry.coordinate().artifactId(), exception.getMessage())
                    )
                );
            }
        }
    }
}
