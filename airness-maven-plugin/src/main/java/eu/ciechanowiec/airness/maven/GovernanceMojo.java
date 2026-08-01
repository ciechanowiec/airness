package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.Repository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * What every governance goal does with the verdicts a check produces: print all of them, then fail on
 * any of them.
 *
 * <p>Printing before deciding is the whole of the adoption ramp. Setting
 * {@code airness.governance.enforce} to false withholds the failure and nothing else, so a project
 * taking the harness on sees the same report it would have failed on and can work through it. A goal
 * that skipped the check instead would leave that project with no idea what it was in for, which is how
 * a ramp turns into a permanent exemption.
 *
 * <p>Nothing here reads the code being built. These goals read the repository, which is why none of them
 * honours {@code skipTests} and why a check that shipped as a test would have been wrong twice over: it
 * would answer to a flag about tests, and it would be deselected by a developer narrowing a run to one
 * test class.
 */
public abstract class GovernanceMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    /**
     * Whether a finding fails the build. Every check runs and prints either way.
     */
    @Parameter(property = "airness.governance.enforce", defaultValue = "true")
    private boolean enforce;

    /**
     * The verdicts this goal reports, one per rule the check states.
     *
     * @return the verdicts, clean or otherwise
     */
    protected abstract List<Findings> findings();

    /**
     * Whether this goal has anything to do in the module it was invoked on.
     *
     * @return whether to run, which is always unless a subclass narrows it
     */
    protected boolean applies() {
        return true;
    }

    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        if (this.applies()) {
            this.report(this.gather());
        } else {
            this.getLog().debug("Nothing for this goal to read here");
        }
    }

    /**
     * The working tree root, which every check is rooted at rather than at the module being built.
     *
     * @return the repository root
     */
    protected final Path repositoryRoot() {
        return Repository.rootFrom(this.project.getBasedir().toPath());
    }

    /**
     * The Java source directories of the module being built, main and test alike.
     *
     * @return the source roots that exist on disk
     */
    protected final List<Path> moduleSourceRoots() {
        return sourceRoots(Stream.of(this.project));
    }

    /**
     * The Java source directories of every module in the reactor, which is what a repository-wide check
     * over sources has to read.
     *
     * @return the source roots that exist on disk
     */
    protected final List<Path> reactorSourceRoots() {
        return sourceRoots(this.session.getAllProjects().stream());
    }

    /**
     * The module being built.
     *
     * @return the project
     */
    protected final MavenProject project() {
        return this.project;
    }

    /**
     * The session this goal runs in.
     *
     * @return the session
     */
    protected final MavenSession session() {
        return this.session;
    }

    private static List<Path> sourceRoots(Stream<MavenProject> projects) {
        return projects
            .flatMap(project -> Stream.concat(
                project.getCompileSourceRoots().stream(), project.getTestCompileSourceRoots().stream()
            ))
            .map(Path::of)
            .filter(Files::isDirectory)
            .distinct()
            .toList();
    }

    private List<Findings> gather() throws MojoExecutionException {
        try {
            return this.findings();
        } catch (RuntimeException exception) {
            throw new MojoExecutionException(exception.getMessage(), exception);
        }
    }

    private void report(Collection<Findings> findings) throws MojoFailureException {
        List<Findings> broken = findings.stream().filter(verdict -> !verdict.clean()).toList();
        broken.forEach(verdict -> this.getLog().error(verdict.report()));
        this.decide(broken.size());
    }

    private void decide(int broken) throws MojoFailureException {
        if (broken > 0 && this.enforce) {
            throw new MojoFailureException(
                broken + " rule(s) reported findings, each printed above with the offences it found"
            );
        }
        if (broken > 0) {
            this.getLog().warn(
                "airness.governance.enforce is false, so the findings above do not fail this build"
            );
        }
    }
}
