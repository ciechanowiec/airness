package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.Repository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

/**
 * What every governance goal does with the verdicts a check produces: print all of them, then fail on
 * any of them.
 *
 * <p>Printing before deciding is the whole of the adoption ramp. Setting
 * {@code airness.enforce} to false withholds the failure and nothing else, so a project
 * taking the harness on sees the same report it would have failed on and can work through it. A goal
 * that skipped the check instead would leave that project with no idea what it was in for, which is how
 * a ramp turns into a permanent exemption.
 *
 * <p>{@code skipTests} is the public bypass for both tests and the harness. It returns before the check
 * gathers findings, so a skipped build cannot accidentally present an old or partial report.
 */
abstract class AbstractGovernanceMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private @Nullable MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private @Nullable MavenSession session;

    /**
     * Whether a finding fails the build. Every check runs and prints either way.
     */
    @Parameter(property = "airness.enforce", defaultValue = "true")
    private boolean enforce;

    /**
     * Whether Maven tests and Airness quality and governance checks are skipped.
     */
    @Parameter(property = "skipTests", defaultValue = "false")
    private boolean skip;

    /**
     * The verdicts this goal reports, one per rule the check states.
     *
     * @return the verdicts, clean or otherwise
     */
    abstract List<Findings> findings();

    /**
     * Whether this goal has anything to do in the module it was invoked on.
     *
     * @return whether to run, which is always unless a subclass narrows it
     */
    boolean applies() {
        return true;
    }

    @Override
    public final void execute() throws MojoFailureException {
        if (this.skip) {
            this.getLog().info("Skipping Airness because skipTests is true");
        } else if (this.applies()) {
            this.report(this.findings());
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
        return Repository.rootFrom(this.project().getBasedir().toPath());
    }

    /**
     * The Java source directories of the module being built, main and test alike.
     *
     * @return the source roots that exist on disk
     */
    protected final List<Path> moduleSourceRoots() {
        return sourceRoots(Stream.of(this.project()));
    }

    /**
     * The resource directories of the module being built, main and test alike.
     *
     * <p>A rule that reads Java and answers with a file needs both trees, since a profile a test names
     * is as ordinarily answered from the test resources as from the main ones. A goal that reads the
     * files themselves asks for the tree it means instead of calling this.
     *
     * @return the resource roots the project declares
     */
    protected final List<Path> moduleResourceRoots() {
        MavenProject current = this.project();
        return Stream.concat(current.getResources().stream(), current.getTestResources().stream())
            .map(Resource::getDirectory)
            .map(Path::of)
            .distinct()
            .toList();
    }

    /**
     * The Java test source directories of the module being built.
     *
     * @return the test source roots that exist on disk
     */
    protected final List<Path> moduleTestSourceRoots() {
        return this.project().getTestCompileSourceRoots().stream()
            .map(Path::of)
            .filter(Files::isDirectory)
            .distinct()
            .toList();
    }

    /**
     * Existing production Java source roots of the current module.
     *
     * @return production source roots
     */
    protected final List<Path> moduleProductionSourceRoots() {
        return this.project().getCompileSourceRoots().stream()
            .map(Path::of)
            .filter(Files::isDirectory)
            .distinct()
            .toList();
    }

    /**
     * Whether the current module contains Java test code.
     *
     * <p>A test source root that exists while holding no Java at all is an ordinary state, and a goal
     * over the tests of such a module has nothing to read. Asking for the sources rather than for the
     * directory keeps that state out of the refusal that a mistyped root earns.
     *
     * @return whether at least one test compile source root contains a Java source file
     */
    protected final boolean hasTestJava() {
        return this.moduleTestSourceRoots().stream().anyMatch(AbstractGovernanceMojo::containsJava);
    }

    /**
     * Whether the current module contains production Java code.
     *
     * @return whether at least one compile source root contains a Java source file
     */
    protected final boolean hasProductionJava() {
        return this.moduleProductionSourceRoots().stream().anyMatch(AbstractGovernanceMojo::containsJava);
    }

    /**
     * Whether the current module contains Java at all, production or test.
     *
     * <p>This is the question a goal over {@link #moduleSourceRoots()} has to ask, for the reason
     * {@link #hasTestJava()} gives: a source root that exists while holding no Java is an ordinary
     * state rather than the mistyped root a refusal is meant for. A goal that gated on the directory
     * instead went on to read nothing and then refused the empty scope, which reaches Maven as an
     * internal plugin error rather than as a verdict, and passes the enforcement switch by.
     *
     * <p>Asking only about production Java would be the same mistake in the other direction, since
     * these roots are the main and test ones together, and a module holding only tests would stop
     * being read without anything saying so.
     *
     * @return whether at least one compile or test compile source root contains a Java source file
     */
    protected final boolean hasModuleJava() {
        return this.moduleSourceRoots().stream().anyMatch(AbstractGovernanceMojo::containsJava);
    }

    /**
     * The Java source directories of every module in the reactor, which is what a repository-wide check
     * over sources has to read.
     *
     * @return the source roots that exist on disk
     */
    protected final List<Path> reactorSourceRoots() {
        return sourceRoots(this.session().getAllProjects().stream());
    }

    /**
     * The module being built.
     *
     * @return the project
     */
    protected final MavenProject project() {
        return Objects.requireNonNull(this.project, "Maven did not inject the current project");
    }

    /**
     * The session this goal runs in.
     *
     * @return the session
     */
    protected final MavenSession session() {
        return Objects.requireNonNull(this.session, "Maven did not inject the current session");
    }

    private static List<Path> sourceRoots(Stream<MavenProject> projects) {
        return projects
            .flatMap(
                project -> Stream.concat(
                    project.getCompileSourceRoots().stream(), project.getTestCompileSourceRoots().stream()
                )
            )
            .map(Path::of)
            .filter(Files::isDirectory)
            .distinct()
            .toList();
    }

    private static boolean containsJava(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.anyMatch(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not inspect sources under " + root, exception);
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
                "airness.enforce is false, so the findings above do not fail this build"
            );
        }
    }
}
