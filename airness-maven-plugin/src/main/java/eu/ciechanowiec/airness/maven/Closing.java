package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.Repository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.maven.project.MavenProject;

/**
 * What the closing step judges once every participating module has completed.
 *
 * <p>The closing step runs once per invocation, inside the module whose completion finished the set. It decides what
 * no single module can: whether the committable tree is still the one the invocation started from, after
 * every module's last execution, and whether the repository as a whole stays under its suppression ceilings.
 * Under the extended profile it also writes the one SBOM and the one compliance report for every module.
 */
@UtilityClass
final class Closing {

    static final String HEADLINE = "Committable files changed during the build";
    static final String UNGOVERNED = "Modules holding code that no Airness parent governs";
    private static final List<String> JAVA_ROOTS = List.of("src/main/java", "src/test/java");
    private static final String MOVED
        = "The working tree content differs from the snapshot the first module took at validate, so a build step"
            + " wrote to a tracked file or the tree was edited while the build ran";
    // Installed packages and build output, which a project that ignores nothing would otherwise have counted.
    private static final List<String> OUTPUT = List.of("node_modules", "dist", ".next", "target");
    private static final String TESTS = "tests";
    private static final Pattern SPEC = Pattern.compile(".*\\.(?:spec|test)\\.[cm]?[jt]sx?");

    /**
     * The modules whose completion closing waits for: every participating module that binds module-complete.
     *
     * @param required the participating modules
     * @return their ids
     */
    static Set<String> awaited(Collection<MavenProject> required) {
        return required.stream().filter(Closing::completes).map(MavenProject::getId)
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * The modules closing covers: every module that completes, and every module without completion that one of
     * them depends on, which Maven finishes before it, sequentially or in parallel. Any other module is left
     * out, whatever order the build happens to finish in, so the verdict never depends on scheduling.
     *
     * @param required the participating modules
     * @param upstream the modules a module depends on, directly or not
     * @return the covered modules, in reactor order
     */
    static List<MavenProject> covered(
        List<MavenProject> required, Function<MavenProject, List<MavenProject>> upstream
    ) {
        Set<MavenProject> reached = required.stream().filter(Closing::completes)
            .flatMap(project -> Stream.concat(Stream.of(project), upstream.apply(project).stream()))
            .collect(Collectors.toUnmodifiableSet());
        return required.stream().filter(reached::contains).toList();
    }

    /**
     * Names every participating module closing leaves out, for the log and the report.
     *
     * @param required the participating modules
     * @param covered  the modules closing covers
     * @return one qualification per module left out
     */
    static List<String> uncovered(List<MavenProject> required, Collection<MavenProject> covered) {
        return required.stream().filter(project -> !covered.contains(project)).map(
            project -> project.getId() + " inherits no Airness parent and no module that completes depends on it, so"
                + " closing does not cover it."
        ).toList();
    }

    /**
     * The participating modules that hold code but inherit no Airness parent, whether or not closing covers them.
     *
     * <p>Such a module binds no Airness goal: no compiler check, analyzer, test rule, coverage floor or node gate
     * judges its code, and a module depending on it says nothing about that code either. A module that holds
     * none, such as a distribution module on a parent of its own, stays allowed, and closing only names it when it
     * leaves it out. Code is a Java source under the module's source roots or the conventional ones, or a
     * {@code package.json} a commit would carry in the module rather than in a child module or its build output.
     *
     * @param required the participating modules
     * @return one offence per module that holds code and completes no Airness verdict
     */
    static Findings ungoverned(Collection<MavenProject> required) {
        return new Findings(
            UNGOVERNED,
            required.stream().filter(project -> !completes(project)).filter(Closing::holdsCode).map(
                project -> project.getId() + " holds code but inherits no Airness parent, so no Airness rule judges"
                    + " it; name airness-parent-java, airness-parent-spring-boot or airness-parent-node as its parent,"
                    + " or move the code into a module that inherits one"
            ).toList()
        );
    }

    /**
     * The final tree comparison: the tree after the last module against the tree before the first.
     *
     * @param unchanged whether the tree matches the session's first snapshot
     * @return the verdict
     */
    static Findings tree(boolean unchanged) {
        return new Findings(HEADLINE, unchanged ? List.of() : List.of(MOVED));
    }

    /**
     * The first-party sources of the node projects, which the node suppression ceiling reads: the files under
     * each member's source roots ({@code src}, {@code scripts} and the {@code sourceRoots} its {@code package.json}
     * adds), without tests, without the generated files the member's {@code generatedArtefacts} name, and without
     * the files the harness owns or a layer declares generated, so the ceiling is earned by code the project wrote.
     *
     * @param root        the repository root
     * @param directories the declared node project directories of every module
     * @param excluded    repository-relative paths of the pinned and generated files the harness and layers place
     * @return the sources
     */
    static List<Path> nodeSources(Path root, Collection<Path> directories, Collection<String> excluded) {
        Set<Path> owned = excluded.stream().map(path -> root.resolve(path).normalize()).collect(Collectors.toSet());
        return directories.stream().distinct().flatMap(
            directory -> committable(directory).stream()
                .filter(path -> OUTPUT.stream().noneMatch(segment -> contains(directory.relativize(path), segment)))
                .filter(path -> !contains(directory.relativize(path), TESTS) && !SPEC.matcher(name(path)).matches())
                .filter(path -> !owned.contains(path.normalize()))
                .filter(path -> NodeMember.of(directory, path).authored(path))
        ).toList();
    }

    private static String name(Path path) {
        return Objects.requireNonNull(path.getFileName(), "a file has a name").toString();
    }

    private static List<Path> committable(Path directory) {
        try {
            return Repository.committableFiles(directory).orElseGet(() -> walked(directory));
        } catch (IllegalStateException _) {
            return walked(directory);
        }
    }

    // Outside a working tree, or without git, every regular file below the directory.
    private static List<Path> walked(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile).toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the node project " + directory, exception);
        }
    }

    private static boolean contains(Path path, String segment) {
        return Stream.iterate(path, Objects::nonNull, Path::getParent)
            .map(Path::getFileName)
            .filter(Objects::nonNull)
            .anyMatch(name -> segment.equals(name.toString()));
    }

    private static boolean holdsCode(MavenProject project) {
        Path basedir = project.getBasedir().toPath();
        Stream<Path> conventional = JAVA_ROOTS.stream().map(basedir::resolve);
        Stream<Path> declared = Stream.concat(
            project.getCompileSourceRoots().stream(), project.getTestCompileSourceRoots().stream()
        ).map(Path::of);
        boolean java = Stream.concat(conventional, declared).distinct().filter(Files::isDirectory)
            .anyMatch(JavaParents::containsJava);
        return java || !NodeManifests.in(basedir.toAbsolutePath().normalize(), owned(project, basedir)).isEmpty();
    }

    // A child module answers for its own directory, and the build directory holds output.
    private static List<Path> owned(MavenProject project, Path basedir) {
        Stream<Path> modules = project.getModules().stream()
            .map(module -> basedir.resolve(module).toAbsolutePath().normalize())
            .map(module -> Files.isRegularFile(module) ? module.resolveSibling("") : module);
        Stream<Path> output = Optional.ofNullable(project.getBuild().getDirectory()).stream()
            .map(directory -> Path.of(directory).toAbsolutePath().normalize());
        return Stream.concat(modules, output).toList();
    }

    private static boolean completes(MavenProject project) {
        return AirnessCoordinates.bindsGoal(project.getBuildPlugins(), CompletionOrder.GOAL);
    }
}
