package eu.ciechanowiec.airness.maven;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession.CloseableSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DefaultDependencyNode;

/**
 * A real Maven container and offline resolver over a private local repository.
 */
final class ReportMavenFixture implements AutoCloseable {

    private final DefaultPlexusContainer container;
    private final CloseableSession resolver;
    private final RepositorySystem system;

    @SneakyThrows
    ReportMavenFixture(Path directory) {
        this.container = new DefaultPlexusContainer(
            new DefaultContainerConfiguration()
                .setClassPathScanning(PlexusConstants.SCANNING_INDEX).setAutoWiring(true)
        );
        this.system = this.container.lookup(RepositorySystem.class);
        this.resolver = this.system.createSessionBuilder().setOffline(true)
            .withLocalRepositoryBaseDirectories(directory.resolve("repository")).build();
    }

    static PluginResolution resolution(String module, String root) {
        return new PluginResolution(
            module, new DefaultArtifact(root), List.of(new DefaultArtifact("example:library:1.0")), Map.of()
        );
    }

    static DefaultDependencyNode node(String coordinate) {
        return new DefaultDependencyNode(new DefaultArtifact(coordinate));
    }

    ProjectBuildingRequest request() {
        return new DefaultProjectBuildingRequest().setRepositorySession(this.resolver);
    }

    @SneakyThrows
    ProjectBuilder builder() {
        return this.container.lookup(ProjectBuilder.class);
    }

    @Override
    public void close() {
        this.resolver.close();
        this.system.close();
        this.container.dispose();
    }
}
