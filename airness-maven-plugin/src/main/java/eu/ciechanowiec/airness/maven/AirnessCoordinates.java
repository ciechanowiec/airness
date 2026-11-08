package eu.ciechanowiec.airness.maven;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;

/**
 * The Maven coordinates Airness publishes under, and what a module's model says about them: which Airness
 * parent the module inherits, whether it is the Airness aggregator, and which goals of the Airness plugin it
 * binds.
 *
 * <p>The parent of a stack is named where that stack is registered, not here.
 */
@UtilityClass
final class AirnessCoordinates {

    /**
     * The groupId of every artifact Airness publishes.
     */
    static final String GROUP = "eu.ciechanowiec";

    /**
     * The artifactId of the Airness aggregator, which every module of the Airness build descends from.
     */
    static final String AGGREGATOR = "airness";

    /**
     * The artifactId of the generic parent, which owns repository-wide work and versions {@code @airness/node}.
     */
    static final String GENERIC_PARENT = "airness-parent";

    /**
     * The artifactId of the parent that carries the Java rules.
     */
    static final String JAVA_PARENT = "airness-parent-java";

    /**
     * The artifactId of the Airness Maven plugin.
     */
    static final String PLUGIN = "airness-maven-plugin";

    /**
     * The Airness Maven plugin as {@code groupId:artifactId}, the form a planned execution names its plugin in.
     */
    static final String PLUGIN_KEY = GROUP + ':' + PLUGIN;

    /**
     * Whether a project is the Airness artifact with an artifactId.
     *
     * @param project    the project
     * @param artifactId the artifactId
     * @return whether the project has the Airness groupId and that artifactId
     */
    static boolean named(MavenProject project, String artifactId) {
        return GROUP.equals(project.getGroupId()) && artifactId.equals(project.getArtifactId());
    }

    /**
     * Whether a project is the Airness aggregator.
     *
     * @param project the project
     * @return whether it is the aggregator
     */
    static boolean isAggregator(MavenProject project) {
        return named(project, AGGREGATOR);
    }

    /**
     * The nearest ancestor of a module that is the Airness artifact with an artifactId.
     *
     * @param module     the module, which is not its own ancestor
     * @param artifactId the artifactId of the Airness parent
     * @return the nearest such ancestor, or nothing when the parent chain holds none
     */
    static Optional<MavenProject> ancestor(MavenProject module, String artifactId) {
        return Stream.iterate(module.getParent(), Objects::nonNull, MavenProject::getParent)
            .filter(parent -> named(parent, artifactId))
            .findFirst();
    }

    /**
     * Whether a module inherits an Airness parent anywhere up its parent chain.
     *
     * @param module     the module
     * @param artifactId the artifactId of the Airness parent
     * @return whether an ancestor is that parent
     */
    static boolean inherits(MavenProject module, String artifactId) {
        return ancestor(module, artifactId).isPresent();
    }

    /**
     * Whether a build plugin is the Airness Maven plugin.
     *
     * @param plugin the build plugin
     * @return whether its groupId and artifactId are the Airness plugin's
     */
    static boolean isPlugin(Plugin plugin) {
        return GROUP.equals(plugin.getGroupId()) && PLUGIN.equals(plugin.getArtifactId());
    }

    /**
     * Whether an execution of the Airness plugin among build plugins runs a goal.
     *
     * @param plugins the build plugins
     * @param goal    the goal
     * @return whether any execution of the Airness plugin runs it
     */
    static boolean bindsGoal(Collection<Plugin> plugins, String goal) {
        return plugins.stream()
            .filter(AirnessCoordinates::isPlugin)
            .flatMap(plugin -> plugin.getExecutions().stream())
            .map(PluginExecution::getGoals)
            .anyMatch(goals -> goals.contains(goal));
    }
}
