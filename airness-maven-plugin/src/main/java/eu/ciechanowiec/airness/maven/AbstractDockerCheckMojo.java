package eu.ciechanowiec.airness.maven;

import java.io.IOException;
import java.util.List;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Runs one repository-wide Docker check while separating findings from operational failures.
 */
abstract class AbstractDockerCheckMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Parameter(property = "airness.enforce", defaultValue = "true")
    private boolean enforce;

    @Parameter(property = "skipTests", defaultValue = "false")
    private boolean skip;

    abstract List<String> command() throws IOException;

    abstract boolean findingsExit(int exit);

    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        if (this.skip) {
            this.getLog().info("Skipping Airness because skipTests is true");
        } else if (OncePerSession.firstRun(this.session, this.getClass())) {
            this.check();
        }
    }

    private void check() throws MojoExecutionException, MojoFailureException {
        this.requireDocker();
        int exit = this.runCheck();
        if (exit != 0) {
            this.fail(exit);
        }
    }

    private void fail(int exit) throws MojoExecutionException, MojoFailureException {
        if (!this.findingsExit(exit)) {
            throw new MojoExecutionException("Docker check failed operationally with exit code " + exit);
        } else if (this.enforce) {
            throw new MojoFailureException("Docker check reported findings (exit code " + exit + ")");
        } else {
            this.getLog().warn("airness.enforce is false, so the Docker findings above do not fail this build");
        }
    }

    protected final MavenProject project() {
        return this.project;
    }

    /**
     * The Maven session whose resolved inputs a Docker-backed check may need.
     *
     * @return the active session
     */
    protected final MavenSession session() {
        return this.session;
    }

    private int runCheck() throws MojoExecutionException {
        try {
            return this.run(this.command());
        } catch (IOException exception) {
            throw new MojoExecutionException("Could not start the Docker check", exception);
        }
    }

    private void requireDocker() throws MojoExecutionException {
        try {
            if (this.runSilently(List.of("docker", "info")) != 0) {
                throw new MojoExecutionException("Docker is installed but its daemon is not reachable");
            }
        } catch (IOException exception) {
            throw new MojoExecutionException("Docker is required for this Airness profile", exception);
        }
    }

    private int run(List<String> command) throws IOException {
        try {
            return new ProcessBuilder(command).inheritIO().start().waitFor();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for " + command.getFirst(), exception);
        }
    }

    private int runSilently(List<String> command) throws IOException {
        try {
            return new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
                .waitFor();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for " + command.getFirst(), exception);
        }
    }
}
