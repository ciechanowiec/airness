package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Every environment variable a compose file needs reaches the places that have to supply it.
 *
 * <p>Two containments, each one-directional. What a compose file interpolates without a default must be
 * documented in the example file, because {@code docker compose config} reads the copy a developer made of
 * it. And it must be supplied by every workflow that verifies, because a workflow has no copied file to
 * interpolate from. A repository with no compose file passes over an empty set, and the first one added
 * is checked from then on.
 *
 * <p>The variables application code reads, and those a build inlines into an image, are inputs a stack
 * layer contributes. The generic layer reads none.
 */
public final class EnvironmentCheck {

    private static final List<String> EXAMPLES = List.of(".env.example", ".env.sample", ".env.template");
    private static final String UNDOCUMENTED
        = "Environment variables a compose file needs that no example file documents (add each to "
            + EXAMPLES.getFirst() + ")";
    private static final String UNSUPPLIED
        = "Environment variables a compose file needs that a verifying workflow does not supply (add each"
            + " to the workflow's env)";

    private final Path root;
    private final ContainerDefinitions definitions;

    /**
     * Names the working tree whose environment declarations are read.
     *
     * @param root the working tree root
     */
    public EnvironmentCheck(Path root) {
        this.root = root;
        this.definitions = new ContainerDefinitions(root);
    }

    /**
     * The variables missing from each place that has to carry them.
     *
     * @return one verdict per containment
     */
    public List<Findings> findings() {
        Set<String> documented = this.example().map(EnvironmentRules::documented).orElseGet(Set::of);
        List<Path> compose = this.definitions.composeFiles();
        List<String> undocumented = compose.stream()
            .flatMap(
                file -> EnvironmentRules.interpolated(this.read(file)).stream()
                    .filter(name -> !documented.contains(name))
                    .map(name -> name + " in " + this.relative(file))
            ).toList();
        Set<String> needed = compose.stream()
            .flatMap(file -> EnvironmentRules.interpolated(this.read(file)).stream())
            .collect(Collectors.toCollection(TreeSet::new));
        List<String> unsupplied = this.definitions.workflows().stream()
            .filter(workflow -> EnvironmentRules.verifying(this.read(workflow)))
            .flatMap(workflow -> this.missing(needed, workflow))
            .toList();
        return List.of(new Findings(UNDOCUMENTED, undocumented), new Findings(UNSUPPLIED, unsupplied));
    }

    private Stream<String> missing(Set<String> needed, Path workflow) {
        Set<String> supplied = EnvironmentRules.supplied(this.read(workflow));
        return needed.stream()
            .filter(name -> !supplied.contains(name))
            .map(name -> name + " in " + this.relative(workflow));
    }

    // One example file per repository, the first present in the declared order. A project that ships two
    // has documented its environment twice, and reading both would let the weaker copy vouch for the other.
    private Optional<String> example() {
        return EXAMPLES.stream()
            .map(name -> Repository.readText(this.root.resolve(name)))
            .flatMap(Optional::stream)
            .findFirst();
    }

    private String read(Path file) {
        return Repository.readText(file).orElse("");
    }

    private String relative(Path file) {
        return this.root.relativize(file).toString();
    }
}
