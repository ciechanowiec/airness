package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.apache.maven.model.Resource;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

/**
 * A module serving user-facing HTML declares how the shared browser sweep reaches it.
 *
 * <p>The sweep belongs to Extended verification, but whether it can run is known from the repository alone,
 * so Default verification states the missing wiring before anything is built. A JSON service and a library
 * are legitimately outside the sweep, and the log says why. {@link WebInterface} states the evidence read.
 */
@Mojo(
    name = "web-interface", defaultPhase = LifecyclePhase.VALIDATE,
    requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true
)
public final class WebInterfaceMojo extends AbstractGovernanceMojo {

    private static final String HEADLINE = "Web interfaces the shared browser sweep can launch";

    /**
     * The module's node project directories, comma-separated and relative to the module.
     */
    @Parameter(property = "airness.node.projects")
    private @Nullable String declared;

    @Override
    boolean applies() {
        return !RepositoryProjects.harnessParent(this.project());
    }

    @Override
    List<Findings> findings() {
        MavenProject module = this.project();
        WebInterface web = of(module);
        this.getLog().info(
            "Web interface: " + (web.detected() ? "detected, " : "not applicable, as ") + web.summary() + " in "
                + module.getArtifactId()
        );
        NodeProjects projects = NodeProjects.of(module.getBasedir().toPath(), this.declared);
        return List.of(new Findings(HEADLINE, web.integrationProblems(module.getArtifactId(), projects)));
    }

    /**
     * Reads whether a module serves user-facing HTML.
     *
     * @param module the module, with its dependencies resolved
     * @return the verdict
     */
    static WebInterface of(MavenProject module) {
        return WebInterface.of(
            module.getBasedir().toPath(), serving(module),
            module.getCompileSourceRoots().stream().map(Path::of).toList(),
            module.getResources().stream().map(Resource::getDirectory).map(Path::of).toList()
        );
    }

    private static boolean serving(MavenProject module) {
        Set<String> stacks = Set.of("spring-webmvc", "spring-webflux");
        return module.getArtifacts().stream().anyMatch(
            artifact -> "org.springframework".equals(artifact.getGroupId())
                && stacks.contains(artifact.getArtifactId()) && !"test".equals(artifact.getScope())
        );
    }
}
