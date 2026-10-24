package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.maven.NodeProjects.NodeProject;
import java.util.List;
import java.util.Objects;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;

/**
 * Runs one {@code package.json} script of a declared node project on the harness's Node and pnpm: the one
 * supported way to build a frontend during the Maven build.
 *
 * <p>The project names the execution and binds it to a phase of its own, such as {@code generate-resources},
 * which comes after the dependencies are installed and before the archive is packed. Only a declared node
 * project is accepted, so a frontend that gets built always gets checked too, and only a script name, so the
 * command lives in {@code package.json} where the gates and the agents can read it. It produces no findings
 * and runs under {@code -DskipTests} too, since skipping it would ship an artifact without its frontend.
 * Build output belongs under {@code target/}: the tree comparison fails a build that changed a tracked file.
 */
@Mojo(name = "node-run", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
public final class NodeRunMojo extends AbstractNodeMojo {

    /**
     * The declared node project directory to run the script in, relative to the module.
     */
    @Parameter(required = true)
    private @Nullable String project;

    /**
     * The name of the {@code package.json} script to run.
     */
    @Parameter(required = true)
    private @Nullable String script;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        String named = Objects.requireNonNull(this.project, "Maven did not inject the node project");
        String run = Objects.requireNonNull(this.script, "Maven did not inject the script");
        NodeProject target = this.target(named, run);
        this.pnpm(
            this.tools(), List.of("run", run), target.directory(),
            target.logs(this.buildDirectory()).resolve("run-" + run + ".log")
        );
    }

    private NodeProject target(String named, String run) throws MojoFailureException {
        try {
            return this.nodeProjects().runnable(this.project().getBasedir().toPath(), named, run);
        } catch (IllegalArgumentException exception) {
            throw new MojoFailureException(exception.getMessage(), exception);
        }
    }
}
