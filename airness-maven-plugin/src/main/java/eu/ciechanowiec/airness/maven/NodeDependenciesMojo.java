package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.maven.NodeProjects.NodeProject;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Installs each declared node project's dependencies from its own lockfile, exactly as locked, and runs no
 * lifecycle script while doing so: neither a dependency's nor the project's own. Whether a script may run is
 * for the gates to judge, and they run later.
 *
 * <p>Bound at {@code initialize} after node-install, so dependencies are present before any phase in which a
 * project could build its frontend with airness:node-run. A declared project without its own lockfile is
 * left alone here. Whether it may lack one is a question for the gates.
 */
@Mojo(name = "node-dependencies", defaultPhase = LifecyclePhase.INITIALIZE, threadSafe = true)
public final class NodeDependenciesMojo extends AbstractNodeMojo {

    private static final List<String> INSTALL = List.of("install", "--frozen-lockfile", "--ignore-scripts");

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        List<NodeProject> locked = this.locked();
        if (locked.isEmpty()) {
            this.getLog().debug("No node project with its own lockfile to install dependencies for here");
        } else {
            NodeTools tools = this.tools();
            for (NodeProject project : locked) {
                this.pnpm(
                    tools, INSTALL, project.directory(), project.logs(this.buildDirectory()).resolve("install.log")
                );
            }
        }
    }

    private List<NodeProject> locked() {
        if (!this.prepares()) {
            return List.of();
        }
        return this.nodeProjects().projects().stream()
            .filter(project -> !project.outside() && project.hasLockfile())
            .toList();
    }
}
