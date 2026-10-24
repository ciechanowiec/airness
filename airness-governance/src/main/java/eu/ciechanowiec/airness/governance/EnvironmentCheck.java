package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
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
 * <p>The Next.js facet contributes a third: what a Next.js application's build configuration inlines through a
 * {@code NEXT_PUBLIC_} read must be declared by an {@code ARG} in every Dockerfile that builds the application,
 * because docker discards a {@code --build-arg} the Dockerfile never named and warns rather than failing. A
 * Dockerfile builds the node package whose directory most closely holds it. The variables application code
 * reads through a validated environment module are a Payload layer input, and the generic layer reads none.
 */
public final class EnvironmentCheck {

    private static final List<String> EXAMPLES = List.of(".env.example", ".env.sample", ".env.template");
    private static final String UNDOCUMENTED
        = "Environment variables a compose file needs that no example file documents (add each to "
            + EXAMPLES.getFirst() + ")";
    private static final String UNSUPPLIED
        = "Environment variables a compose file needs that a verifying workflow does not supply (add each"
            + " to the workflow's env)";

    private static final String UNDECLARED
        = "Environment variables a Next.js build inlines that a Dockerfile building it does not declare (add ARG"
            + " <NAME> to the stage that runs the build)";
    private static final List<String> BUILD_CONFIGURATIONS = List.of(
        "next.config.ts", "next.config.mjs", "next.config.js"
    );
    private static final String MANIFEST = "package.json";
    private static final String NEXT = "next";

    private final Path root;
    private final ContainerDefinitions definitions;
    private final Function<Path, Optional<PackageManifest>> manifests;

    /**
     * Names the working tree whose environment declarations are read, with no node package read.
     *
     * @param root the working tree root
     */
    public EnvironmentCheck(Path root) {
        this(root, _ -> Optional.empty());
    }

    /**
     * Names the working tree whose environment declarations are read, and how a node package is read.
     *
     * @param root      the working tree root
     * @param manifests the {@code package.json} of a directory, empty where it holds none
     */
    public EnvironmentCheck(Path root, Function<Path, Optional<PackageManifest>> manifests) {
        this.root = root;
        this.definitions = new ContainerDefinitions(root);
        this.manifests = manifests;
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
        return List.of(
            new Findings(UNDOCUMENTED, undocumented),
            new Findings(UNSUPPLIED, unsupplied),
            new Findings(UNDECLARED, this.undeclared())
        );
    }

    private List<String> undeclared() {
        List<Path> packages = Repository.trackedFiles(this.root).stream()
            .filter(file -> MANIFEST.equals(String.valueOf(file.getFileName())))
            .map(Path::getParent)
            .toList();
        List<Path> dockerfiles = this.definitions.dockerfiles();
        return packages.stream()
            .filter(this::isNextApplication)
            .flatMap(application -> this.undeclaredIn(application, this.building(application, packages, dockerfiles)))
            .toList();
    }

    private boolean isNextApplication(Path directory) {
        return this.manifests.apply(directory)
            .filter(manifest -> manifest.installs(NEXT))
            .isPresent();
    }

    private Stream<String> undeclaredIn(Path application, List<Path> dockerfiles) {
        if (dockerfiles.isEmpty()) {
            return Stream.empty();
        }
        Set<String> declared = dockerfiles.stream()
            .flatMap(file -> EnvironmentRules.buildArguments(this.read(file)).stream())
            .collect(Collectors.toSet());
        String files = dockerfiles.stream().map(this::relative).collect(Collectors.joining(", "));
        return BUILD_CONFIGURATIONS.stream()
            .map(application::resolve)
            .flatMap(file -> EnvironmentRules.inlined(this.read(file)).stream())
            .distinct()
            .sorted()
            .filter(name -> !declared.contains(name))
            .map(name -> name + " in " + files);
    }

    // The Dockerfiles that build a package: those its directory holds more closely than any other package's
    // does, and, wherever they sit, those that name its directory, which is how a Dockerfile at the root of a
    // monorepo, or under docker/, copies one application out of it.
    private List<Path> building(Path application, List<Path> packages, List<Path> dockerfiles) {
        String directory = this.relative(application);
        return dockerfiles.stream().filter(file -> this.builds(file, application, directory, packages)).toList();
    }

    private boolean builds(Path dockerfile, Path application, String directory, List<Path> packages) {
        boolean held = owner(dockerfile, packages).filter(application::equals).isPresent();
        boolean named = !directory.isEmpty() && this.read(dockerfile).contains(directory);
        return held || named;
    }

    private static Optional<Path> owner(Path file, List<Path> packages) {
        return packages.stream().filter(file::startsWith).max(Comparator.comparingInt(Path::getNameCount));
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
