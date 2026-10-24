package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.maven.NodeProjects.NodeProject;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Installs each declared node project's dependencies from its own lockfile, exactly as locked, with every
 * lifecycle script switched off, and then runs the build scripts of exactly the dependencies the project
 * approves with {@code true} in {@code allowBuilds} of its {@code pnpm-workspace.yaml}. The project's own
 * lifecycle scripts never run, an undecided dependency's script stays unrun rather than failing the install,
 * no setting that would approve every dependency at once takes effect, and no pnpmfile hook runs. Whether
 * the record decides every dependency that has a script, and only those, is the install-scripts gate's to
 * judge.
 *
 * <p>Bound at {@code initialize} after node-install, so dependencies are present before any phase in which a
 * project could build its frontend with airness:node-run. A declared project without its own lockfile is
 * left alone here. Whether it may lack one is a question for the gates.
 */
@Mojo(name = "node-dependencies", defaultPhase = LifecyclePhase.INITIALIZE, threadSafe = true)
public final class NodeDependenciesMojo extends AbstractNodeMojo {

    private static final String NO_PNPMFILE = "--ignore-pnpmfile";
    private static final List<String> INSTALL = List.of(
        "install", "--frozen-lockfile", "--ignore-scripts", NO_PNPMFILE
    );
    private static final List<String> APPROVED_BUILDS = List.of(
        "rebuild", NO_PNPMFILE, "--config.strict-dep-builds=false", "--config.dangerously-allow-all-builds=false"
    );

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        List<NodeProject> locked = this.locked();
        if (locked.isEmpty()) {
            this.getLog().debug("No node project with its own lockfile to install dependencies for here");
        } else {
            NodeTools tools = this.tools();
            for (NodeProject project : locked) {
                Path logs = project.logs(this.buildDirectory());
                this.pnpm(tools, INSTALL, project.directory(), logs.resolve("install.log"));
                this.pnpm(tools, APPROVED_BUILDS, project.directory(), logs.resolve("rebuild.log"));
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
