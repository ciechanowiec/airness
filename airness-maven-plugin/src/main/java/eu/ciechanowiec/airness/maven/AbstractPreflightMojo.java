package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Repository;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * A goal that runs before the checks do, and asks whether they can mean anything.
 *
 * <p>These deliberately ignore {@code airness.enforce}. That switch withholds a failure about
 * the code, which a project taking the harness on has a reason to want. What it must never withhold is a
 * failure about the harness itself: a source root that names nothing and a clone with no history do not
 * make the checks lenient, they make the checks lie, and a green build is then a statement about a tree
 * nobody read.
 */
abstract class AbstractPreflightMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    /**
     * Whether every Airness check is bypassed.
     */
    @Parameter(property = "skipTests", defaultValue = "false")
    private boolean skip;

    /**
     * Everything wrong with the way this build is set up, one sentence each.
     *
     * @return the problems, empty when there are none
     */
    abstract List<String> problems();

    @Override
    public final void execute() throws MojoFailureException {
        if (this.skip) {
            this.getLog().info("Skipping Airness because skipTests is true");
        } else if (this.applies()) {
            this.decide(this.problems());
        }
    }

    boolean applies() {
        return RepositoryProjects.owns(this.session.getTopLevelProject(), this.project)
            && OncePerSession.firstRun(this.session, this.getClass());
    }

    /**
     * The working tree root, asked of git rather than assumed from the module being built.
     *
     * @return the repository root
     */
    protected final Path repositoryRoot() {
        return Repository.rootFrom(this.project.getBasedir().toPath());
    }

    /**
     * The current project whose harness configuration is being checked.
     *
     * @return the current project
     */
    protected final MavenProject project() {
        return this.project;
    }

    /**
     * The Maven session shared by every module in the reactor.
     *
     * @return the active session
     */
    protected final MavenSession session() {
        return this.session;
    }

    private void decide(Collection<String> problems) throws MojoFailureException {
        problems.forEach(problem -> this.getLog().error(problem));
        if (!problems.isEmpty()) {
            throw new MojoFailureException(
                problems.size() + " problem(s) would leave a later check reporting on nothing"
            );
        }
    }
}
