package eu.ciechanowiec.airness.maven;

import java.util.List;
import java.util.Map;
import org.eclipse.aether.artifact.Artifact;

/**
 * One module's effective build tool and the graph Maven resolved for it.
 *
 * @param module       owning module
 * @param root         requested plugin or extension
 * @param artifacts    selected artifact versions
 * @param dependencies resolved dependency edges
 */
record PluginResolution(
    String module, Artifact root, List<Artifact> artifacts, Map<String, List<String>> dependencies
) {

    PluginResolution {
        artifacts = List.copyOf(artifacts);
        dependencies = Map.copyOf(dependencies);
    }
}
