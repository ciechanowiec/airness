package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.BuildInput;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;

/**
 * Resolves compiler and resource selections from the current effective Maven project.
 */
final class ProjectBuildInputs {

    private static final String COMPILER = "maven-compiler-plugin";
    private static final String RESOURCES = "maven-resources-plugin";
    private final MavenProject project;
    private final Path root;
    private final List<Path> outputs;
    private final InputExecutions executions;

    ProjectBuildInputs(MavenProject project, Collection<Path> outputs, Path root, Collection<String> phases) {
        this.project = project;
        this.root = root;
        this.outputs = List.copyOf(outputs);
        this.executions = new InputExecutions(project, Set.copyOf(phases));
    }

    List<BuildInput> selections() {
        return Stream.of(
            this.javaInputs(false), this.javaInputs(true), this.resources(false), this.resources(true)
        ).flatMap(stream -> stream).toList();
    }

    private Stream<BuildInput> javaInputs(boolean tests) {
        String goal = tests ? "testCompile" : "compile";
        return this.executions.configurations(COMPILER, goal).stream().flatMap(
            config -> this.javaInputs(config, tests)
        );
    }

    private Stream<BuildInput> javaInputs(InputConfiguration config, boolean tests) {
        List<String> roots = this.sourceRoots(config, tests);
        List<String> includes = config.values(tests ? "testIncludes" : "includes");
        List<String> excludes = config.values(tests ? "testExcludes" : "excludes");
        String kind = tests ? "test Java" : "main Java";
        return roots.stream().map(this::path)
            .filter(this::repositoryInput).map(directory -> new InputSelection(directory, includes, excludes, true))
            .map(selection -> this.javaInput(selection, kind));
    }

    private List<String> sourceRoots(InputConfiguration config, boolean tests) {
        List<String> declared = config.values("compileSourceRoots");
        List<String> defaults = tests ? this.project.getTestCompileSourceRoots() : this.project.getCompileSourceRoots();
        return declared.isEmpty() ? defaults : declared;
    }

    private BuildInput javaInput(InputSelection selection, String kind) {
        Set<String> names = Set.copyOf(
            selection.files(this.outputs).stream().filter(name -> name.endsWith(".java")).toList()
        );
        return new BuildInput(selection.directory(), this.project.getArtifactId() + " " + kind, names);
    }

    private Stream<BuildInput> resources(boolean tests) {
        return this.executions.configurations(RESOURCES, tests ? "testResources" : "resources").stream()
            .flatMap(config -> this.resources(config, tests));
    }

    private Stream<BuildInput> resources(InputConfiguration config, boolean tests) {
        List<Resource> defaults = tests ? this.project.getTestResources() : this.project.getResources();
        List<InputConfiguration> overrides = config.children("resources").stream()
            .flatMap(parent -> parent.children("resource").stream()).toList();
        Stream<Resource> resources = overrides.isEmpty() ? defaults.stream() : overrides.stream().map(
            ProjectBuildInputs::resource
        );
        String kind = tests ? "test resource" : "main resource";
        return resources.filter(resource -> this.repositoryInput(this.path(resource.getDirectory())))
            .map(resource -> this.resourceInput(resource, kind, config.defaultExcludes()));
    }

    private static Resource resource(InputConfiguration config) {
        Resource resource = new Resource();
        resource.setDirectory(config.value("directory").orElseThrow());
        resource.setIncludes(config.values("includes"));
        resource.setExcludes(config.values("excludes"));
        return resource;
    }

    private BuildInput resourceInput(Resource resource, String kind, boolean defaults) {
        Path directory = this.path(resource.getDirectory());
        InputSelection selection = new InputSelection(
            directory, resource.getIncludes(), resource.getExcludes(), defaults
        );
        return new BuildInput(directory, this.project.getArtifactId() + " " + kind, selection.files(this.outputs));
    }

    private Path path(String value) {
        boolean unresolved = value.contains("${");
        if (unresolved) {
            throw new IllegalStateException("Cannot resolve Maven build input path: " + value);
        }
        return this.project.getBasedir().toPath().resolve(value).toAbsolutePath().normalize();
    }

    private boolean repositoryInput(Path directory) {
        return directory.startsWith(this.root) && this.outputs.stream().noneMatch(directory::startsWith);
    }
}
