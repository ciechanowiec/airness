package eu.ciechanowiec.airness.maven;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

/**
 * What the node goals that build rather than judge share: the declared node projects, the harness's Node
 * and pnpm, and running pnpm with its output in the build log.
 *
 * <p>These goals are not checks, so {@code airness.enforce} does not reach them and a failure of theirs is a
 * failed build step. Under {@code -DskipTests} the installing goals still run when the module binds
 * airness:node-run, because that goal builds the frontend into the artifact. A module that binds none
 * installs nothing in Skip mode.
 */
abstract class AbstractNodeMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private @Nullable MavenProject mavenProject;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private @Nullable MavenSession mavenSession;

    /**
     * The module's node project directories, comma-separated and relative to the module.
     */
    @Parameter(property = NodeProjects.PROPERTY)
    private @Nullable String declared;

    /**
     * Whether Maven tests and Airness quality and governance checks are skipped.
     */
    @Parameter(property = "skipTests", defaultValue = "false")
    private boolean skip;

    /**
     * Installs the pinned Node and pnpm into the cache under the local Maven repository, unless a build has
     * already installed them there.
     *
     * @param session the session, which names the local repository
     * @param log     where to say what was installed
     * @return the executables
     * @throws IOException when the toolchain cannot be downloaded, verified or unpacked
     */
    static NodeTools install(MavenSession session, Log log) throws IOException {
        NodeToolchain toolchain = NodeToolchain.pinned();
        Path cache = NodeToolchain.cache(
            session.getRepositorySession().getLocalRepositoryManager().getRepository().getBasePath()
        );
        NodeTools tools = toolchain.installIn(cache);
        log.info(toolchain.description() + ", verified by SHA-256, at " + cache);
        return tools;
    }

    /**
     * Writes a command's log into the build log, line by line.
     *
     * @param file the command's log file
     * @param log  the build log
     */
    static void replay(Path file, Log log) {
        try (Stream<String> lines = Files.lines(file)) {
            lines.forEach(log::info);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + file, exception);
        }
    }

    /**
     * Whether the installing goals have work in this module: it is a consumer, it declares a node project, and
     * either the build is not skipping or the module builds a frontend into its artifact.
     *
     * @return whether to install
     */
    final boolean prepares() {
        return !RepositoryProjects.harnessParent(this.project())
            && !this.nodeProjects().none()
            && (!this.skip || NodeProjects.buildsWithNodeRun(this.project().getBuildPlugins()));
    }

    /**
     * The installed toolchain.
     *
     * @return the executables
     * @throws MojoExecutionException when the toolchain cannot be installed
     */
    final NodeTools tools() throws MojoExecutionException {
        try {
            return install(this.session(), this.getLog());
        } catch (IOException exception) {
            throw new MojoExecutionException("Could not install the pinned Node and pnpm", exception);
        }
    }

    /**
     * Runs pnpm in a directory, replays its output, and fails the build on a nonzero exit.
     *
     * @param tools     the executables
     * @param arguments the pnpm arguments
     * @param directory the working directory
     * @param log       where the output goes
     * @throws MojoExecutionException when pnpm cannot be started or outlives its timeout
     * @throws MojoFailureException   when pnpm exits nonzero
     */
    final void pnpm(
        NodeTools tools, List<String> arguments, Path directory, Path log
    ) throws MojoExecutionException, MojoFailureException {
        String described = "pnpm " + String.join(" ", arguments) + " in " + directory;
        this.getLog().info("Running " + described);
        int exit = exit(tools, arguments, directory, log);
        replay(log, this.getLog());
        if (exit != 0) {
            throw new MojoFailureException(described + " exited " + exit + "; its output is above and in " + log);
        }
    }

    private static int exit(
        NodeTools tools, List<String> arguments, Path directory, Path log
    ) throws MojoExecutionException {
        try {
            return tools.pnpm(arguments, directory, log);
        } catch (IOException exception) {
            throw new MojoExecutionException(
                "Could not run pnpm " + String.join(" ", arguments) + " in " + directory, exception
            );
        }
    }

    /**
     * The node projects the module declares.
     *
     * @return the declared projects
     */
    final NodeProjects nodeProjects() {
        return NodeProjects.of(this.project().getBasedir().toPath(), this.declared);
    }

    /**
     * The module's build directory, beneath which every node project's results go.
     *
     * @return the build directory
     */
    final Path buildDirectory() {
        return Path.of(this.project().getBuild().getDirectory());
    }

    /**
     * The module being built.
     *
     * @return the project
     */
    final MavenProject project() {
        return Objects.requireNonNull(this.mavenProject, "Maven did not inject the current project");
    }

    private MavenSession session() {
        return Objects.requireNonNull(this.mavenSession, "Maven did not inject the current session");
    }
}
