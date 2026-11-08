package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptorBuilder;
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

    private static final Path DESCRIPTOR = Path.of("target", "classes", "META-INF", "maven", "plugin.xml");

    @Test
    @SneakyThrows
    void requiresTheRuntimeDependenciesThatARepackagedArchiveVendors() {
        try (Reader descriptor = Files.newBufferedReader(DESCRIPTOR)) {
            MojoDescriptor goal = new PluginDescriptorBuilder().build(descriptor).getMojo("artifact-content");
            assertEquals(
                ResolutionScope.RUNTIME.id(), goal.getDependencyResolutionRequired(),
                "the vendored-bytes exemption compares against the runtime dependencies Maven resolved"
            );
        }
    }
}
