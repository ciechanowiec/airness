package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import eu.ciechanowiec.airness.governance.DeclaredCoordinate;
import java.util.List;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The effective build names every bound and managed plugin and each dependency a plugin is given, whether or
 * not the model carries a version for it.
 */
class PluginCoordinatesTest {

    @Test
    void namesBoundAndManagedPluginsAndThePluginDependencies() {
        Plugin frontend = plugin("com.github.eirslett", "frontend-maven-plugin", "1.15.1");
        Dependency core = new Dependency();
        core.setGroupId("com.github.eirslett");
        core.setArtifactId("frontend-plugin-core");
        frontend.addDependency(core);
        Plugin managed = plugin("org.codehaus.mojo", "exec-maven-plugin", null);
        assertEquals(
            List.of(
                new DeclaredCoordinate("com.github.eirslett", "frontend-maven-plugin", "1.15.1"),
                new DeclaredCoordinate("com.github.eirslett", "frontend-plugin-core", ""),
                new DeclaredCoordinate("org.codehaus.mojo", "exec-maven-plugin", "")
            ),
            PluginCoordinates.of(List.of(frontend), List.of(managed, frontend))
        );
    }

    private static Plugin plugin(String group, String artifact, @Nullable String version) {
        Plugin plugin = new Plugin();
        plugin.setGroupId(group);
        plugin.setArtifactId(artifact);
        plugin.setVersion(version);
        return plugin;
    }
}
