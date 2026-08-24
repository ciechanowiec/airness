package eu.ciechanowiec.airness.maven;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
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

    private static final int SUCCESS = 0;
    private static final List<String> DOCKER_INFO = List.of("docker", "info");
    /**
     * A repository, an optional tag, and a digest. The digest is what makes this a pin: a tag can be
     * republished against different bytes, so an image named by tag alone lets an upstream release change
     * a verdict here while nothing in the repository changed.
     */
    private static final Pattern PINNED = Pattern.compile(
        "^[a-z0-9][a-z0-9._/-]*(:[A-Za-z0-9][A-Za-z0-9._-]*)?@sha256:[0-9a-f]{64}$"
    );

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Parameter(property = "airness.enforce", defaultValue = "true")
    private boolean enforce;

    @Parameter(property = "skipTests", defaultValue = "false")
    private boolean skip;

    abstract List<String> command() throws IOException;

    /**
     * The image this check runs, so that the pin can be read before the daemon is asked for anything.
     *
     * @return the configured image reference
     */
    abstract String image();

    abstract boolean findingsExit(int exit);

    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        if (this.skip) {
            this.getLog().info("Skipping Airness because skipTests is true");
        } else if (this.firstRun()) {
            this.check();
        }
    }

    private boolean firstRun() {
        return OncePerSession.firstRun(
            this.session.getRepositorySession().getData(), this.getClass()
        );
    }

    private void check() throws MojoExecutionException, MojoFailureException {
        this.requirePinnedImage();
        this.requireDocker();
        int exit = this.runCheck();
        if (exit != 0) {
            this.fail(exit);
        }
    }

    private void fail(int exit) throws MojoExecutionException, MojoFailureException {
        if (this.findingsExit(exit)) {
            this.reportFindings(exit);
            return;
        }
        throw new MojoExecutionException("Docker check failed operationally with exit code " + exit);
    }

    private void reportFindings(int exit) throws MojoFailureException {
        if (this.enforce) {
            throw new MojoFailureException("Docker check reported findings (exit code " + exit + ")");
        }
        this.getLog().warn("airness.enforce is false, so the Docker findings above do not fail this build");
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

    /**
     * Read before the daemon is, deliberately. The pin is a property of the configuration rather than of
     * the machine, so a tag-only image is the same finding on a machine that has no Docker at all.
     */
    private void requirePinnedImage() throws MojoExecutionException {
        String reference = this.image();
        if (!PINNED.matcher(reference).matches()) {
            throw new MojoExecutionException(
                "Pin the image by digest (repository:tag@sha256:...) rather than by tag, because a tag is "
                    + "republishable and a verdict would change while this repository did not: " + reference
            );
        }
    }

    private void requireDocker() throws MojoExecutionException {
        try {
            if (this.runSilently() != SUCCESS) {
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

    private int runSilently() throws IOException {
        try {
            return new ProcessBuilder(DOCKER_INFO)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
                .waitFor();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for " + DOCKER_INFO.getFirst(), exception);
        }
    }
}
