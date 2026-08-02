package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * No declared dependency trails the latest stable release by the failing bound that
 * {@link DependencyFreshnessRules} states. Calendar versions beginning with {@code 20**} compare their
 * leading year, and other schemes with no comparable level are skipped.
 *
 * <p>The registry read can fail, and when it does the check fails with it. A dependency whose latest
 * release could not be read is not a dependency known to be current, so passing on missing data would
 * turn an outage into a green build, which is the one outcome this check must not have.
 */
public final class DependencyFreshnessCheck {

    /**
     * The registry every project reads unless it says otherwise. It is stated here rather than inside
     * {@link MavenCentral}, so the one host this check reaches by default is a value a caller can see
     * and replace rather than one compiled into the lookup.
     */
    public static final String CENTRAL = "https://repo1.maven.org/maven2/";

    private static final String HEADLINE = "Dependencies trailing by the major-version bound";

    private final String registry;
    private final List<DeclaredDependency> dependencies;

    /**
     * Reads the declared dependencies, without yet asking the registry about any of them.
     *
     * @param pom      the POM whose declared dependencies are read
     * @param registry the base URL of the registry to ask, such as {@link #CENTRAL}
     */
    public DependencyFreshnessCheck(Path pom, String registry) {
        this(DeclaredDependencies.from(pom), registry);
    }

    /**
     * Uses dependencies whose versions have already been resolved from Maven's effective model.
     *
     * @param dependencies directly declared, effective dependencies
     * @param registry     registry base URL
     */
    public DependencyFreshnessCheck(List<DeclaredDependency> dependencies, String registry) {
        this.registry = registry;
        this.dependencies = dependencies.stream()
            .filter(dependency -> DependencyFreshnessRules.hasComparableMajor(dependency.version()))
            .toList();
    }

    /**
     * How many dependencies carry a comparable level and are therefore asked about, which a caller logs
     * so the reach of a clean verdict is on the record.
     *
     * @return the number of dependencies in scope
     */
    public int scanned() {
        return this.dependencies.size();
    }

    /**
     * Every dependency trailing by the bound, one per entry.
     *
     * @return the single verdict this check produces
     */
    public List<Findings> findings() {
        return List.of(
            new Findings(HEADLINE, this.dependencies.stream().flatMap(this::violation).toList())
        );
    }

    private Stream<String> violation(DeclaredDependency dependency) {
        Optional<String> reported = DependencyFreshnessRules.violation(
            dependency, MavenCentral.latestMajor(this.registry, dependency)
        );
        return reported.stream();
    }
}
