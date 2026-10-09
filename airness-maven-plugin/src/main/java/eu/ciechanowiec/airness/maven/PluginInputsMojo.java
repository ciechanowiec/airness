package eu.ciechanowiec.airness.maven;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import lombok.SneakyThrows;
import org.apache.maven.lifecycle.internal.GoalTask;
import org.apache.maven.lifecycle.internal.LifecycleExecutionPlanCalculator;
import org.apache.maven.lifecycle.internal.LifecycleTask;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.internal.PluginDependenciesResolver;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.filter.ScopeDependencyFilter;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;

/**
 * Supplies the vulnerability scanner with the plugin dependencies Maven actually resolves.
 *
 * <p>Dependency-Check's native plugin scan resolves published POMs without plugin dependency overrides.
 * That reports replaced versions and misses their replacements. Maven's own resolver applies those
 * overrides. The execution plan also distinguishes active tools from unused lifecycle defaults.
 */
@Mojo(name = "plugin-inputs", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, threadSafe = true)
public final class PluginInputsMojo extends AbstractPreflightMojo {

    private static final String NO_EXCEPTIONS = "<suppressions xmlns=\""
        + "https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.3.xsd\"/>\n";

    private final LifecycleExecutionPlanCalculator plans;
    private final PluginDependenciesResolver resolver;

    /**
     * Uses the same planning and resolution components as Maven's plugin loader.
     *
     * @param plans    Maven's execution planner
     * @param resolver Maven's plugin dependency resolver
     */
    @Inject
    public PluginInputsMojo(LifecycleExecutionPlanCalculator plans, PluginDependenciesResolver resolver) {
        this.plans = plans;
        this.resolver = resolver;
    }

    @Override
    boolean applies() {
        return true;
    }

    @Override
    @SneakyThrows
    List<String> problems() {
        boolean self = RepositoryProjects.selfBuild(this.session().getTopLevelProject(), this.project());
        List<MavenProject> projects = self ? this.session().getProjects() : List.of(this.project());
        Map<String, Path> artifacts = projects.stream().flatMap(this::artifacts)
            .collect(
                Collectors.toUnmodifiableMap(
                    Artifact::toString, artifact -> Objects.requireNonNull(artifact.getFile()).toPath(),
                    (first, _) -> first
                )
            );
        Path directory = this.project().getBasedir().toPath().resolve(this.project().getBuild().getDirectory())
            .resolve("airness/plugin-inputs");
        new PluginScanFiles(directory).write(artifacts);
        ScannerResources.copy("eclipse-suppressions.xml", directory);
        ScannerResources.copy("tool-suppressions.xml", directory);
        policy(self, "self-tool-suppressions.xml", directory);
        boolean spring = artifacts.keySet().stream()
            .anyMatch(coordinate -> coordinate.startsWith("org.springframework.boot:spring-boot-loader-tools:"));
        policy(spring, "spring-tool-suppressions.xml", directory);
        this.getLog().info("Plugin vulnerability inputs: " + artifacts.size() + " resolved artifact(s)");
        return List.of();
    }

    @SneakyThrows
    private static void policy(boolean applies, String resource, Path directory) {
        if (applies) {
            ScannerResources.copy(resource, directory);
        } else {
            Files.writeString(directory.resolve(resource), NO_EXCEPTIONS);
        }
    }

    @SneakyThrows
    private Stream<Artifact> artifacts(MavenProject project) {
        List<Object> tasks = Stream.concat(Stream.of("clean", "deploy"), this.session().getGoals().stream())
            .distinct().map(PluginInputsMojo::task).toList();
        List<MojoExecution> executions = this.plans.calculateExecutionPlan(this.session(), project, tasks, false)
            .getMojoExecutions();
        Stream<Plugin> active = Stream.concat(
            executions.stream().map(execution -> effective(project, execution)),
            project.getBuildPlugins().stream().filter(Plugin::isExtensions)
        );
        return Stream.concat(active, project.getBuildExtensions().stream().map(PluginInputsMojo::extension))
            .distinct().flatMap(plugin -> this.artifacts(project, plugin));
    }

    @SneakyThrows
    private Stream<Artifact> artifacts(MavenProject project, Plugin plugin) {
        Artifact root = this.resolver.resolve(
            plugin, project.getRemotePluginRepositories(), this.session().getRepositorySession()
        );
        DependencyNode resolved = this.resolver.resolve(
            plugin, root, new ScopeDependencyFilter("provided", "test"), project.getRemotePluginRepositories(),
            this.session().getRepositorySession()
        );
        PreorderNodeListGenerator files = new PreorderNodeListGenerator();
        resolved.accept(files);
        this.getLog().debug("Resolved " + plugin.getId() + ": " + files.getArtifacts(false));
        return files.getArtifacts(false).stream();
    }

    private static Object task(String goal) {
        return goal.contains(":") ? new GoalTask(goal) : new LifecycleTask(goal);
    }

    private static Plugin effective(MavenProject project, MojoExecution execution) {
        Plugin declared = Optional.ofNullable(project.getPlugin(execution.getPlugin().getKey()))
            .orElse(execution.getPlugin());
        Plugin plugin = new Plugin();
        plugin.setGroupId(execution.getGroupId());
        plugin.setArtifactId(execution.getArtifactId());
        plugin.setVersion(execution.getVersion());
        plugin.setDependencies(declared.getDependencies());
        return plugin;
    }

    private static Plugin extension(Extension extension) {
        Plugin plugin = new Plugin();
        plugin.setGroupId(extension.getGroupId());
        plugin.setArtifactId(extension.getArtifactId());
        plugin.setVersion(extension.getVersion());
        return plugin;
    }
}
