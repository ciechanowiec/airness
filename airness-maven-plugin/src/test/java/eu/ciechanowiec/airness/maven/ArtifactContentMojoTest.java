package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.junit.jupiter.api.Test;

/**
 * The artifact-content goal sees the dependencies a packaging plugin can copy into the archive it inspects.
 *
 * <p>Maven fills the project artifacts only for a goal whose descriptor requires their resolution, so the
 * descriptor this module's build writes is read rather than the annotation: it is what Maven itself binds,
 * whatever spelling the annotation is written in.
 */
class ArtifactContentMojoTest {

    @Test
    void requiresTheRuntimeDependenciesThatARepackagedArchiveVendors() {
        MojoDescriptor goal = PluginGoals.all().stream()
            .filter(mojo -> "artifact-content".equals(mojo.getGoal()))
            .findFirst()
            .orElseThrow();
        assertEquals(
            ResolutionScope.RUNTIME.id(), goal.getDependencyResolutionRequired(),
            "the vendored-bytes exemption compares against the runtime dependencies Maven resolved"
        );
    }
}
