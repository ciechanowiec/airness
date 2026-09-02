package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Repository;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

/**
 * Runs one repository-wide Docker check while separating findings from operational failures.
 */
abstract class AbstractDockerCheckMojo extends AbstractMojo {

    private static final int SUCCESS = 0;
    private static final List<String> DOCKER_INFO = List.of("docker", "info");
    private static final String MOUNT = "/repo";
    /**
     * Read inside the image the check itself runs, from the mount the check itself takes. The listing
     * proves the directory can be walked, the Git pointer proves a file can be opened whether .git is a
     * directory or the file a worktree leaves behind, and the top-level files prove it was not one lucky
     * file. The shell is the POSIX one, because the gitleaks image carries busybox and the Qodana image
     * carries dash.
     */
    private static final String PROBE = "set -e; cd /repo; ls -A . > /dev/null;"
        + " if [ -d .git ]; then cat .git/HEAD > /dev/null; else cat .git > /dev/null; fi;"
        + " find . -maxdepth 1 -type f -exec cat {} + > /dev/null";
    private static final String UNREADABLE = " cannot be read inside a container, so this"
        + " check would report on nothing. Docker mounted it read-only and a shell in the image could not list"
        + " it or read its files. On macOS with Colima this is the privacy protection of Downloads, Desktop and"
        + " Documents: grant the terminal that starts Colima access to the folder under System Settings,"
        + " Privacy and Security, Files and Folders, then restart Colima, or keep the repository outside those"
        + " folders.";
    /**
     * A repository, an optional tag, and a digest. The digest is what makes this a pin: a tag can be
     * republished against different bytes, so an image named by tag alone lets an upstream release change
     * a verdict here while nothing in the repository changed.
     */
    private static final Pattern PINNED = Pattern.compile(
        "^[a-z0-9][a-z0-9._/-]*(:[A-Za-z0-9][A-Za-z0-9._-]*)?@sha256:[0-9a-f]{64}$"
    );

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private @Nullable MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private @Nullable MavenSession session;

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

    /**
     * The findings this check can name from the report it leaves behind.
     *
     * <p>A check that writes no machine-readable report names none, and its failure message says only that
     * the run found something. The container writes its own output to this build's console either way.
     *
     * @return one line per finding, in the order the report holds them
     */
    List<String> findings() {
        return List.of();
    }

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
            this.session().getRepositorySession().getData(), this.getClass()
        );
    }

    private void check() throws MojoExecutionException, MojoFailureException {
        this.requirePinnedImage();
        this.requireDocker();
        this.requireReadableRepository();
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
            throw new MojoFailureException(this.findingsMessage(exit));
        }
        this.getLog().warn("airness.enforce is false, so the Docker findings above do not fail this build");
    }

    private String findingsMessage(int exit) {
        return Stream.concat(
            Stream.of("Docker check reported findings (exit code " + exit + ")"),
            this.findings().stream().map(finding -> "  " + finding)
        ).collect(Collectors.joining(System.lineSeparator()));
    }

    protected final MavenProject project() {
        return Objects.requireNonNull(this.project, "Maven did not inject the current project");
    }

    /**
     * The Maven session whose resolved inputs a Docker-backed check may need.
     *
     * @return the active session
     */
    protected final MavenSession session() {
        return Objects.requireNonNull(this.session, "Maven did not inject the current session");
    }

    /**
     * The root of the working tree every Docker check mounts, read through Git so that a module built
     * from a subdirectory still mounts the whole repository.
     *
     * @return the repository root
     */
    protected final Path repositoryRoot() {
        return Repository.rootFrom(this.project().getBasedir().toPath());
    }

    private int runCheck() throws MojoExecutionException {
        try {
            return this.run(this.command(), ProcessBuilder.Redirect.INHERIT);
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
            if (this.run(DOCKER_INFO, ProcessBuilder.Redirect.DISCARD) != SUCCESS) {
                throw new MojoExecutionException("Docker is installed but its daemon is not reachable");
            }
        } catch (IOException exception) {
            throw new MojoExecutionException("Docker is required for this Airness profile", exception);
        }
    }

    /**
     * The daemon answering is not the daemon seeing the files. A bind mount the host refuses to serve
     * arrives inside the container as a tree whose every open fails, and a scanner reading such a tree
     * reports on nothing or exits the way it exits for a finding. So one shell in the same image reads
     * the same mount before the check is trusted with it.
     */
    private void requireReadableRepository() throws MojoExecutionException {
        Path root = this.repositoryRoot();
        try {
            if (this.run(probeCommand(root, this.image()), ProcessBuilder.Redirect.DISCARD) != SUCCESS) {
                throw new MojoExecutionException("The repository at " + root + UNREADABLE);
            }
        } catch (IOException exception) {
            throw new MojoExecutionException("Could not start the container that reads " + root, exception);
        }
    }

    static List<String> probeCommand(Path root, String image) {
        return List.of(
            "docker", "run", "--rm", "-v", root + ":" + MOUNT + ":ro",
            "--entrypoint", "/bin/sh", image, "-c", PROBE
        );
    }

    private int run(List<String> command, ProcessBuilder.Redirect streams) throws IOException {
        try {
            return new ProcessBuilder(command)
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .redirectOutput(streams)
                .redirectError(streams)
                .start()
                .waitFor();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for " + command.getFirst(), exception);
        }
    }
}
