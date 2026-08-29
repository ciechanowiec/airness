package eu.ciechanowiec.airness.maven;

import java.util.Collection;
import lombok.experimental.UtilityClass;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;

/**
 * Whether a module is the one that gets deployed.
 *
 * <p>Airness pins the version of the Boot plugin and binds none of its goals, because which module of a
 * reactor becomes the executable archive belongs to the project rather than to the harness. That leaves
 * the binding itself as the only statement of which module that is, and it is a better statement than
 * any property would be: it is already there, it is already true, and it cannot fall out of step with
 * the build because it is the thing that does the building.
 */
@UtilityClass
final class BootRepackaging {

    private static final String GROUP = "org.springframework.boot";
    private static final String ARTIFACT = "spring-boot-maven-plugin";
    private static final String GOAL = "repackage";

    /**
     * Whether the Boot plugin is bound to repackage this module.
     *
     * @param plugins the build plugins of the module, as the effective model resolved them
     * @return whether an execution of the repackage goal is declared
     */
    static boolean applies(Collection<Plugin> plugins) {
        return plugins.stream()
            .filter(BootRepackaging::boot)
            .flatMap(plugin -> plugin.getExecutions().stream())
            .map(PluginExecution::getGoals)
            .flatMap(Collection::stream)
            .anyMatch(GOAL::equals);
    }

    private static boolean boot(Plugin plugin) {
        return GROUP.equals(plugin.getGroupId()) && ARTIFACT.equals(plugin.getArtifactId());
    }
}
