package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Repository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
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
    // A reachable daemon answers docker info in seconds, so one silent for a minute is wedged.
    private static final Duration DAEMON_ANSWER = Duration.ofMinutes(1);
    // A probe starts the check's own image, which the first run on a machine pulls before the probe reads.
    private static final Duration PROBE_RUN = Duration.ofMinutes(20);
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
     * The container runs this check needs to succeed before it starts, after the repository mount was read.
     *
     * <p>Each runs in the check's own image. A directory mounted besides the repository is read there, so
     * a mount the host does not serve fails with its path rather than as a check that runs on nothing, and
     * a check whose tool can find nothing in a readable mount proves first that it finds what it will read.
     *
     * @return the probes, none for a check that reads only the repository
     * @throws MojoExecutionException when a probe cannot be built from the host
     */
    List<MountProbe> probes() throws MojoExecutionException {
        return List.of();
    }

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
        requireDocker();
        Path root = this.repositoryRoot();
        requireReadable(
            MountProbe.repositoryCommand(root, this.image()), root, MountProbe.unreadableRepository(root)
        );
        for (MountProbe probe : this.probes()) {
            requireReadable(probe.command(), probe.mount(), probe.unreadable());
        }
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

    private static void requireDocker() throws MojoExecutionException {
        try {
            if (ScannerCommand.quiet(DOCKER_INFO, DAEMON_ANSWER) != SUCCESS) {
                throw new MojoExecutionException("Docker is installed but its daemon is not reachable");
            }
        } catch (TimeoutException exception) {
            throw new MojoExecutionException(
                "The Docker daemon accepted the connection but did not answer docker info within "
                    + DAEMON_ANSWER.toSeconds() + " seconds; restart Docker and run the build again", exception
            );
        } catch (IOException exception) {
            throw new MojoExecutionException("Docker is required for this Airness profile", exception);
        }
    }

    /**
     * One shell in the same image reads the same mount before the check is trusted with it. See
     * {@link MountProbe} for what each probe proves.
     */
    private static void requireReadable(
        List<String> probe, Path mount, String unreadable
    ) throws MojoExecutionException {
        try {
            if (ScannerCommand.quiet(probe, PROBE_RUN) != SUCCESS) {
                throw new MojoExecutionException(unreadable);
            }
        } catch (TimeoutException exception) {
            throw new MojoExecutionException(
                "The container that reads " + mount + " did not finish within " + PROBE_RUN.toMinutes()
                    + " minutes; check that the Docker daemon is answering", exception
            );
        } catch (IOException exception) {
            throw new MojoExecutionException("Could not start the container that reads " + mount, exception);
        }
    }

    // The check itself is not bounded: on a large repository it legitimately runs long, and any limit
    // would be a tuning knob that fails correct builds. The daemon answered docker info before it starts.
    private int run(List<String> command) throws IOException {
        try {
            return new ProcessBuilder(command)
                .inheritIO()
                .start()
                .waitFor();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for " + command.getFirst(), exception);
        }
    }

    static int executeScanner(
        ScannerProcess process, String executable, List<String> arguments
    ) throws IOException {
        Path report = process.tree().directory().resolve("report.json");
        ScannerCommand.run(process.probeCommand(), process.tree().directory().resolve("mount-probe.txt"));
        Files.deleteIfExists(report);
        return ScannerCommand.execute(process.command(executable, arguments), report);
    }
}
