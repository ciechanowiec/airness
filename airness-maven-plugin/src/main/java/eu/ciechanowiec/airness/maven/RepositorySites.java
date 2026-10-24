package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.AssetLocation;
import eu.ciechanowiec.airness.governance.AssetSite;
import eu.ciechanowiec.airness.governance.Capability;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;

/**
 * The directories of a repository that receive managed files, with the capabilities present in each.
 *
 * <p>Read from the model of every {@code pom.xml} in the working tree, built without building the modules
 * themselves. Reading every project file rather than the reactor, or the modules one aggregator lists, is
 * what makes the answer the same whichever module a build started in: two builds that share a repository
 * root but no aggregator would otherwise each demand root blocks without the other's stack, and no tree
 * could satisfy both.
 */
@UtilityClass
final class RepositorySites {

    private static final String GROUP = "eu.ciechanowiec";
    private static final String SELF = "airness";
    private static final String JAVA_PARENT = "airness-parent-java";
    private static final String PAYLOAD_PARENT = "airness-parent-payload";
    private static final String NODE_PROJECTS = "airness.node.projects";
    private static final String POM = "pom.xml";
    private static final String PACKAGE = "package.json";
    private static final String NEXT = "next";
    private static final List<String> ROUTE_DIRECTORIES = List.of("src/app", "src/pages");
    private static final List<String> HOOK_DIRECTORIES = List.of("", "src/");
    private static final List<String> HOOK_NAMES = List.of("middleware", "proxy");
    private static final List<String> HOOK_EXTENSIONS = List.of(".ts", ".js", ".mts", ".mjs", ".cts", ".cjs");
    private static final String SEEDED_PROXY = "src/proxy.ts";
    private static final String SEPARATOR = ",";
    private static final String PARENT_DIRECTORY = "..";
    private static final Set<String> PRUNED = Set.of("target", "node_modules", ".git");

    /**
     * Reads the sites of the repository from every project file in it.
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
        try {
            return of(
                root, builder.build(poms, false, models).stream().map(ProjectBuildingResult::getProject).toList()
            );
        } catch (ProjectBuildingException exception) {
            throw new IllegalStateException(
                "Could not read the models of the project files in " + root + ", which decide the managed files",
                exception
            );
        }
    }

    /**
     * Works out the sites from the models of every module in the repository.
     *
     * <p>A module inheriting {@code airness-parent-java} is Java, and so is a module of the Airness build
     * itself, whose modules inherit its aggregator rather than the parent they publish. A module inheriting
     * {@code airness-parent-payload} is Payload. Every directory holding a {@code package.json} that a module
     * lists in {@code airness.node.projects} is a node project, and a Payload module's node projects are
     * Payload projects as well. A listed directory without a {@code package.json} receives nothing, so a
     * typo never has files written into it. The parents Airness publishes are not consumers, so a default
     * one of them declares for its children says nothing about the parent's own directory.
     *
     * @param root    the repository root
     * @param modules the models of every module in the repository
     * @return the root site, carrying every capability present anywhere, then one site per node project
     *         directory, carrying the union of what every module listing it says it is
     */
    static List<AssetSite> of(Path root, Collection<MavenProject> modules) {
        Path repository = real(root);
        List<MavenProject> consumers = modules.stream()
            .filter(module -> !RepositoryProjects.harnessParent(module))
            .toList();
        Map<String, Set<Capability>> projects = new LinkedHashMap<>();
        consumers.forEach(module -> declare(projects, repository, module));
        List<AssetSite> nodes = projects.entrySet().stream()
            .flatMap(project -> sites(repository, project.getKey(), project.getValue()))
            .toList();
        Set<Capability> present = Stream.concat(
            consumers.stream().flatMap(RepositorySites::capabilities),
            nodes.stream().flatMap(site -> site.capabilities().stream())
        ).collect(Collectors.toSet());
        return Stream.concat(Stream.of(AssetSite.root(present)), nodes.stream()).toList();
    }

    // Every node project is a site for the node project's files. A Next.js application whose routes live under
    // src and which has no request hook of its own is a second site at the same directory, for the Next.js
    // facet's files: the proxy seed is written into src, where such an application loads it, and an application
    // that already runs a middleware or a proxy would get a second hook, which Next.js refuses or ignores.
    private static Stream<AssetSite> sites(Path repository, String directory, Set<Capability> capabilities) {
        AssetSite node = new AssetSite(directory, AssetLocation.NODE_PROJECT, capabilities);
        Path project = repository.resolve(directory);
        boolean next = PackageManifests.in(project)
            .filter(manifest -> manifest.installs(NEXT))
            .isPresent();
        return next && routesUnderSource(project) && !ownsRequestHook(project)
            ? Stream.of(node, new AssetSite(directory, AssetLocation.NEXT_PROJECT, capabilities))
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

    private static void declare(Map<String, Set<Capability>> projects, Path repository, MavenProject module) {
        Set<Capability> present = nodeCapabilities(module);
        nodeDirectories(repository, module).forEach(
            directory -> projects.computeIfAbsent(directory, _ -> EnumSet.noneOf(Capability.class)).addAll(present)
        );
    }

    private static Stream<Capability> capabilities(MavenProject module) {
        return Stream.of(
            Optional.of(Capability.JAVA).filter(_ -> inherits(module, JAVA_PARENT) || selfBuild(module)),
            Optional.of(Capability.PAYLOAD).filter(_ -> inherits(module, PAYLOAD_PARENT))
        ).flatMap(Optional::stream);
    }

    private static Set<Capability> nodeCapabilities(MavenProject module) {
        return inherits(module, PAYLOAD_PARENT)
            ? Set.of(Capability.NODE, Capability.PAYLOAD)
            : Set.of(Capability.NODE);
    }

    private static Stream<String> nodeDirectories(Path repository, MavenProject module) {
        Path basedir = real(module.getBasedir().toPath());
        return Arrays.stream(module.getProperties().getProperty(NODE_PROJECTS, "").split(SEPARATOR))
            .map(String::strip)
            .filter(entry -> !entry.isEmpty())
            .map(entry -> basedir.resolve(entry).normalize())
            .filter(directory -> Files.isRegularFile(directory.resolve(PACKAGE)))
            .map(directory -> relative(repository, directory));
    }

    private static String relative(Path repository, Path directory) {
        Path relative = repository.relativize(directory);
        if (relative.startsWith(PARENT_DIRECTORY)) {
            throw new IllegalStateException(
                "The node project " + directory + " lies outside the repository " + repository
                    + "; list only directories inside it in " + NODE_PROJECTS
            );
        }
        return relative.toString().replace(File.separatorChar, '/');
    }

    private static boolean inherits(MavenProject module, String parent) {
        return Stream.iterate(module.getParent(), Objects::nonNull, MavenProject::getParent)
            .anyMatch(ancestor -> GROUP.equals(ancestor.getGroupId()) && parent.equals(ancestor.getArtifactId()));
    }

    private static boolean selfBuild(MavenProject module) {
        return Optional.ofNullable(module.getParent())
            .filter(parent -> GROUP.equals(parent.getGroupId()) && SELF.equals(parent.getArtifactId()))
            .isPresent();
    }

    private static List<File> projectFiles(Path root) {
        return searched(root).map(Path::toFile).toList();
    }

    // Lists one directory and descends into every subdirectory except build output, installed node
    // packages and Git metadata, which can be large and hold no project file of this repository.
    private static Stream<Path> searched(Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            List<Path> listed = entries.sorted().toList();
            return listed.stream().flatMap(RepositorySites::found);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not search " + directory + " for project files", exception);
        }
    }

    private static Stream<Path> found(Path entry) {
        String name = entry.getFileName().toString();
        if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
            return PRUNED.contains(name) ? Stream.empty() : searched(entry);
        }
        return Stream.of(entry).filter(_ -> POM.equals(name));
    }

    private static Path real(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not resolve " + path, exception);
        }
    }
}
