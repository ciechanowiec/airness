package eu.ciechanowiec.airness.maven;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.project.MavenProject;

/**
 * The version each harness npm package must resolve to in a module's node projects, read from the Airness
 * parents in the module's Maven parent chain: {@code @airness/node} at the version of {@code airness-parent},
 * which {@code airness-parent-java} and {@code airness-parent-node} share, and {@code ploaness} at the version
 * of {@code airness-parent-payload}, which ploaness releases on its own schedule.
 *
 * <p>The wiring gate reads them from the environment variable this names, because no file of a node project
 * can state which parent the Maven module inherits.
 *
 * @param versions each package's expected version, in the order its parent was found
 */
record HarnessVersions(SequencedMap<String, String> versions) {

    /**
     * The environment variable a node gate reads the versions from.
     */
    static final String VARIABLE = "AIRNESS_HARNESS_VERSIONS";

    /**
     * The environment variable a node gate reads the module's own version from. A harness package's own
     * repository, such as ploaness-node, which inherits {@code airness-parent-node}, releases the package at
     * that version, and no parent in its chain names it.
     */
    static final String MODULE_VARIABLE = "AIRNESS_MODULE_VERSION";

    /**
     * The environment variable a node gate reads the module's own identity from, as {@code groupId:artifactId}.
     * Only the module that builds a harness package may name its node project after it and link it to itself.
     * the mapping lives in airness-node's {@code harness-packages.ts}.
     */
    static final String IDENTITY_VARIABLE = "AIRNESS_MODULE";

    private static final String GROUP = "eu.ciechanowiec";
    private static final Map<String, String> PACKAGES = Map.of(
        "airness-parent", "@airness/node",
        "airness-parent-payload", "ploaness"
    );

    /**
     * Copies the versions into an unmodifiable map, so they cannot change after the chain was read.
     *
     * @param versions each package's expected version
     */
    HarnessVersions {
        versions = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(versions));
    }

    /**
     * Reads the versions from a module's parent chain.
     *
     * @param project the module
     * @return the versions its Airness parents name
     */
    static HarnessVersions of(MavenProject project) {
        return of(
            Stream.iterate(project.getParent(), Objects::nonNull, MavenProject::getParent)
                .map(parent -> List.of(parent.getGroupId(), parent.getArtifactId(), parent.getVersion()))
                .toList()
        );
    }

    /**
     * Reads the versions from a parent chain given as coordinates, nearest parent first.
     *
     * @param chain each ancestor's groupId, artifactId and version
     * @return the versions the Airness parents among them name
     */
    static HarnessVersions of(List<List<String>> chain) {
        SequencedMap<String, String> versions = new LinkedHashMap<>();
        chain.stream()
            .filter(ancestor -> GROUP.equals(ancestor.getFirst()) && PACKAGES.containsKey(ancestor.get(1)))
            .forEach(ancestor -> versions.putIfAbsent(PACKAGES.get(ancestor.get(1)), ancestor.getLast()));
        return new HarnessVersions(versions);
    }

    /**
     * The value of the environment variable: {@code name=version} pairs separated by commas.
     *
     * @return the value, empty when no Airness parent is in the chain
     */
    String value() {
        return this.versions.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining(","));
    }
}
