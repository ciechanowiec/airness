package eu.ciechanowiec.airness.maven;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * The gate ids the module's bound node-gate executions own.
 *
 * <p>A gate run from the command line adds a gate. It may not take an id a bound execution owns, because it
 * would rewrite that gate's result file and evidence after the bound run had written them.
 */
@UtilityClass
final class NodeGateOwnership {

    private static final String GROUP = "eu.ciechanowiec";
    private static final String PLUGIN = "airness-maven-plugin";
    private static final String GOAL = "node-gate";

    /**
     * The gate ids bound executions of the module's effective build name.
     *
     * @param plugins the module's build plugins
     * @return the owned gate ids
     */
    static Set<String> bound(Collection<Plugin> plugins) {
        return plugins.stream()
            .filter(plugin -> GROUP.equals(plugin.getGroupId()) && PLUGIN.equals(plugin.getArtifactId()))
            .flatMap(plugin -> plugin.getExecutions().stream())
            .filter(execution -> execution.getGoals().contains(GOAL))
            .map(NodeGateOwnership::gate)
            .flatMap(Optional::stream)
            .collect(Collectors.toUnmodifiableSet());
    }

    private static Optional<String> gate(PluginExecution execution) {
        return Optional.ofNullable(execution.getConfiguration())
            .filter(Xpp3Dom.class::isInstance)
            .map(Xpp3Dom.class::cast)
            .map(configuration -> configuration.getChild("gate"))
            .map(Xpp3Dom::getValue)
            .map(String::strip);
    }
}
