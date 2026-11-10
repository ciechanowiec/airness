package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.AssetLocation;
import eu.ciechanowiec.airness.governance.AssetSite;
import eu.ciechanowiec.airness.governance.Layer;
import eu.ciechanowiec.airness.governance.Repository;
import eu.ciechanowiec.airness.maven.NodeProjects.NodeProject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;

/**
 * The directories of a repository that receive managed files, with the layers present in each.
 *
 * <p>Read from the model of every {@code pom.xml} a commit from the working tree would carry, built without
 * building the modules themselves. Reading every project file rather than the reactor, or the modules one
 * aggregator lists, is what makes the answer the same whichever module a build started in: two builds that share
 * a repository root but no aggregator would otherwise each demand root blocks without the other's stack, and no
 * tree could satisfy both.
 *
 * <p>Git says which files those are, as it does for every other reader of the repository: what it ignores, such
 * as build output, a scratch checkout, a worktree nested in the clone or a directory a container owns, is no
 * project of this repository, and is not even listed, so an unreadable one stops nothing. A project file that is
 * data rather than a module is passed over too: one under a resource directory, such as an archetype's template
 * or a test fixture, and one under {@code src/it}, which an integration test copies and filters before Maven ever
 * reads it.
 */
@UtilityClass
final class RepositorySites {

    private static final String POM = "pom.xml";
    private static final String NEXT = "next";
    private static final List<String> ROUTE_DIRECTORIES = List.of("src/app", "src/pages");
    private static final List<String> HOOK_DIRECTORIES = List.of("", "src/");
    private static final List<String> HOOK_NAMES = List.of("middleware", "proxy");
    private static final List<String> HOOK_EXTENSIONS = List.of(".ts", ".js", ".mts", ".mjs", ".cts", ".cjs");
    private static final String SEEDED_PROXY = "src/proxy.ts";
    private static final String PARENT_DIRECTORY = "..";
    // Output and installed packages, which a project that ignores nothing would otherwise have offered, then a
    // resource directory and the integration tests' templates, which hold project files as data.
    private static final Pattern DATA = Pattern.compile(
        "(?:^|/)(?:target|node_modules|src/it|src/[^/]+/resources)/"
    );

    /**
     * The parent of each stack, by artifactId, with the layer a module inheriting it carries. A stack's
     * entry is the whole of its registration here.
     */
    private static final Map<String, Layer> STACK_PARENTS = Map.of("airness-parent-payload", Layer.PAYLOAD);

    /**
     * Reads the sites of the repository from every project file in it.
     *
     * <p>A project file Maven cannot build into a model is left out rather than failing the read. It is none of
     * this build's modules, which Maven built before any goal ran, and a module that cannot be built carries no
     * stack: the build that selects it fails on that file in its own words.
     *
     * @param builder the Maven project builder
     * @param request the session's project building request
     * @param root    the repository root
     * @return the root site first, then one site per node project directory
     */
    static List<AssetSite> read(ProjectBuilder builder, ProjectBuildingRequest request, Path root) {
        List<File> poms = projectFiles(root);
        ProjectBuildingRequest models = new DefaultProjectBuildingRequest(request);
        models.setProcessPlugins(false);
        models.setResolveDependencies(false);
        return of(
            root, results(builder, poms, models).stream()
                .filter(RepositorySites::built)
                .map(ProjectBuildingResult::getProject)
                .toList()
        );
    }

    private static List<ProjectBuildingResult> results(
        ProjectBuilder builder, List<File> poms, ProjectBuildingRequest request
    ) {
        try {
            return builder.build(poms, false, request);
        } catch (ProjectBuildingException exception) {
            return Optional.ofNullable(exception.getResults()).orElse(List.of());
        }
    }

    private static boolean built(ProjectBuildingResult result) {
        return Optional.ofNullable(result.getProject()).isPresent() && result.getProblems().stream()
            .noneMatch(problem -> problem.getSeverity() != ModelProblem.Severity.WARNING);
    }

    /**
     * Works out the sites from the models of every module in the repository.
     *
     * <p>A module inheriting {@code airness-parent-java} is Java, and so is a module of the Airness build
     * itself, whose modules inherit its aggregator rather than the parent they publish. A module inheriting
     * the parent of a stack carries that stack's layer. Every directory holding a {@code package.json}
     * that a module lists in {@code airness.node.projects} is a node project, and carries as well each
     * layer of the module that refines the node layer. A listed directory without a
     * {@code package.json} receives nothing, so a typo never has files written into it. The parents Airness
     * publishes are not consumers, so a default one of them declares for its children says nothing about the
     * parent's own directory.
     *
     * @param root    the repository root
     * @param modules the models of every module in the repository
     * @return the root site, carrying every layer present anywhere, then one site per node project
     *         directory, carrying the union of what every module listing it says it is
     */
    static List<AssetSite> of(Path root, Collection<MavenProject> modules) {
        Path repository = real(root);
        List<MavenProject> consumers = modules.stream()
            .filter(module -> !RepositoryProjects.harnessParent(module))
            .toList();
        Map<String, Set<Layer>> projects = new LinkedHashMap<>();
        consumers.forEach(module -> declare(projects, repository, module));
        List<AssetSite> nodes = projects.entrySet().stream()
            .flatMap(project -> sites(repository, project.getKey(), project.getValue()))
            .toList();
        Set<Layer> present = Stream.concat(
            consumers.stream().flatMap(RepositorySites::layers),
            nodes.stream().flatMap(site -> site.layers().stream())
        ).collect(Collectors.toSet());
        return Stream.concat(Stream.of(AssetSite.root(present)), nodes.stream()).toList();
    }

    // Every node project is a site for the node project's files. A Next.js application whose routes live under
    // src and which has no request hook of its own is a second site at the same directory, for the Next.js
    // facet's files: the proxy seed is written into src, where such an application loads it, and an application
    // that already runs a middleware or a proxy would get a second hook, which Next.js refuses or ignores.
    private static Stream<AssetSite> sites(Path repository, String directory, Set<Layer> layers) {
        AssetSite node = new AssetSite(directory, AssetLocation.NODE_PROJECT, layers);
        Path project = repository.resolve(directory);
        boolean next = PackageManifests.in(project)
            .filter(manifest -> manifest.installs(NEXT))
            .isPresent();
        return next && routesUnderSource(project) && !ownsRequestHook(project)
            ? Stream.of(node, new AssetSite(directory, AssetLocation.NEXT_PROJECT, layers))
            : Stream.of(node);
    }

    private static boolean routesUnderSource(Path project) {
        return ROUTE_DIRECTORIES.stream().anyMatch(routes -> Files.isDirectory(project.resolve(routes)));
    }

    private static boolean ownsRequestHook(Path project) {
        return HOOK_DIRECTORIES.stream()
            .flatMap(RepositorySites::hookFiles)
            .filter(file -> !SEEDED_PROXY.equals(file))
            .anyMatch(file -> Files.isRegularFile(project.resolve(file)));
    }

    private static Stream<String> hookFiles(String directory) {
        return HOOK_NAMES.stream()
            .flatMap(name -> HOOK_EXTENSIONS.stream().map(extension -> directory + name + extension));
    }

    private static void declare(Map<String, Set<Layer>> projects, Path repository, MavenProject module) {
        Set<Layer> present = nodeLayers(module);
        nodeDirectories(repository, module).forEach(
            directory -> projects.computeIfAbsent(directory, _ -> EnumSet.noneOf(Layer.class)).addAll(present)
        );
    }

    private static Stream<Layer> layers(MavenProject module) {
        Stream<Layer> java = Stream.of(Layer.JAVA)
            .filter(
                _ -> AirnessCoordinates.inherits(module, AirnessCoordinates.JAVA_PARENT)
                    || RepositoryProjects.selfModule(module)
            );
        Stream<Layer> stacks = STACK_PARENTS.entrySet().stream()
            .filter(stack -> AirnessCoordinates.inherits(module, stack.getKey()))
            .map(Map.Entry::getValue);
        return Stream.concat(java, stacks);
    }

    private static Set<Layer> nodeLayers(MavenProject module) {
        Stream<Layer> refining = layers(module).filter(layer -> layer.refines(Layer.NODE));
        return Stream.concat(Stream.of(Layer.NODE), refining).collect(Collectors.toSet());
    }

    private static Stream<String> nodeDirectories(Path repository, MavenProject module) {
        String declared = module.getProperties().getProperty(NodeProjects.PROPERTY);
        return NodeProjects.of(real(module.getBasedir().toPath()), declared).projects().stream()
            .filter(NodeProject::hasManifest)
            .map(project -> relative(repository, project.directory()));
    }

    private static String relative(Path repository, Path directory) {
        Path relative = repository.relativize(directory);
        if (relative.startsWith(PARENT_DIRECTORY)) {
            throw new IllegalStateException(
                "The node project " + directory + " lies outside the repository " + repository
                    + "; list only directories inside it in " + NodeProjects.PROPERTY
            );
        }
        return relative.toString().replace(File.separatorChar, '/');
    }

    private static List<File> projectFiles(Path root) {
        List<Path> committable = Repository.committableFiles(root).orElseThrow(
            () -> new IllegalStateException(
                "Git could not list the files of " + root + ", whose project files decide the managed files"
            )
        );
        return committable.stream()
            .map(file -> root.relativize(file).toString().replace(File.separatorChar, '/'))
            .filter(file -> POM.equals(file) || file.endsWith('/' + POM))
            .filter(file -> !DATA.matcher(file).find())
            .sorted()
            .map(file -> root.resolve(file).toFile())
            .toList();
    }

    private static Path real(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not resolve " + path, exception);
        }
    }
}
