package eu.ciechanowiec.airness.maven;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;

/**
 * Resolves active compiler and resource execution configurations.
 *
 * @param project the current effective Maven project
 * @param phases  the lifecycle phases supplied by Maven
 */
record InputExecutions(MavenProject project, Set<String> phases) {

    InputExecutions {
        phases = Set.copyOf(phases);
    }

    List<InputConfiguration> configurations(String artifact, String goal) {
        Optional<Plugin> plugin = this.project.getBuildPlugins().stream()
            .filter(item -> "org.apache.maven.plugins".equals(item.getGroupId()))
            .filter(item -> artifact.equals(item.getArtifactId())).findFirst();
        InputConfiguration common = new InputConfiguration(plugin.map(Plugin::getConfiguration));
        List<PluginExecution> declared = plugin.stream().flatMap(item -> item.getExecutions().stream()).toList();
        String defaultId = "default-" + goal;
        Optional<PluginExecution> standard = declared.stream()
            .filter(execution -> defaultId.equals(execution.getId())).findFirst();
        Stream<InputConfiguration> defaults = standard.filter(this::bound)
            .map(execution -> common.merged(Optional.ofNullable(execution.getConfiguration()))).stream();
        Stream<InputConfiguration> implicit = standard.isEmpty() ? Stream.of(common) : Stream.empty();
        Stream<InputConfiguration> additional = declared.stream()
            .filter(execution -> execution.getGoals().contains(goal)).filter(this::bound)
            .filter(execution -> !defaultId.equals(execution.getId()))
            .map(PluginExecution::getConfiguration).map(Optional::ofNullable).map(common::merged);
        return Stream.of(defaults, implicit, additional).flatMap(stream -> stream).toList();
    }

    private boolean bound(PluginExecution execution) {
        return Optional.ofNullable(execution.getPhase()).map(this.phases::contains).orElse(true);
    }
}
