package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.Repository;
import eu.ciechanowiec.airness.governance.SuppressionBudgetCheck;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.maven.model.PluginExecution;
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

    /*
     * Three suppressions per thousand lines of Java source, with the smallest ceiling of five
     * holding underneath the rate. The rate leaves room for justified analyzer boundaries that code cannot
     * remove while keeping suppressions sparse as a project grows.
     *
     * Deliberately not a parameter. A ceiling a project can raise is a ceiling that gets raised on the change
     * that would otherwise have failed, which is the one moment the ceiling exists to catch.
     */
    static final double RATE = 3;

    private static final String HEADLINE = "Committable files changed during the build";
    private static final String MOVED
        = "The working tree content differs from the snapshot the first module took at validate, so a build step"
            + " wrote to a tracked file or the tree was edited while the build ran";
    // Installed packages and build output, which a project that ignores nothing would otherwise have counted.
    private static final List<String> OUTPUT = List.of("node_modules", "dist", ".next", "target");
    private static final String TESTS = "tests";
    private static final Pattern SPEC = Pattern.compile(".*\\.(?:spec|test)\\.[cm]?[jt]sx?");
    private static final String AIRNESS_PLUGIN = "eu.ciechanowiec:airness-maven-plugin";

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
     * The final tree comparison: the tree after the last module against the tree before the first.
     *
     * @param unchanged whether the tree matches the session's first snapshot
     * @return the verdict
     */
    static Findings tree(boolean unchanged) {
        return new Findings(HEADLINE, unchanged ? List.of() : List.of(MOVED));
    }

    /**
     * The repository-wide suppression ceiling over the Java sources of every module.
     *
     * @param root      the repository root
     * @param javaRoots the Java source roots of every module
     * @return the check
     */
    static SuppressionBudgetCheck javaSuppressions(Path root, List<Path> javaRoots) {
        return new SuppressionBudgetCheck(root, javaRoots, RATE);
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

    private static boolean completes(MavenProject project) {
        return project.getBuildPlugins().stream()
            .filter(plugin -> AIRNESS_PLUGIN.equals(plugin.getGroupId() + ':' + plugin.getArtifactId()))
            .flatMap(plugin -> plugin.getExecutions().stream())
            .map(PluginExecution::getGoals)
            .anyMatch(goals -> goals.contains(CompletionOrder.GOAL));
    }
}
