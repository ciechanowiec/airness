package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.ProjectBuilder;
import org.jspecify.annotations.Nullable;

/**
 * Every {@code package.json} in the module is a node project a gate checks, every declared node project
 * exists, and every authored script and stylesheet in the module is a file one of those projects checks.
 *
 * <p>Runs in every module during Default and Extended verification, whether or not the module declares a
 * node project, because the module that declares nothing is the one where a new, undeclared frontend would
 * otherwise go unchecked. Only Skip mode turns it off. It runs before any tool is installed, so missing
 * wiring is named before the expensive verification starts, and it rewrites nothing.
 */
@Mojo(name = "node-projects", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public final class NodeProjectsMojo extends AbstractGovernanceMojo {

    /**
     * The module's node project directories, comma-separated and relative to the module.
     */
    @Parameter(property = NodeProjects.PROPERTY)
    private @Nullable String declared;

    /**
     * Repository-relative paths of further files a generator owns, comma-separated, which the frontend
     * inventory passes over as generated.
     */
    @Parameter(property = "airness.generated.files")
    private @Nullable String generated;

    /**
     * Repository-relative paths this project has taken over from the harness, comma-separated, which the
     * frontend inventory reads as authored rather than as placed by the harness.
     */
    @Parameter(property = "airness.assets.unmanaged")
    private @Nullable String unmanaged;

    private final ProjectBuilder builder;

    /**
     * Receives the builder that reads the models of every module in the repository, from which the generated
     * files of the stack layers present are placed.
     *
     * @param builder the Maven project builder
     */
    @Inject
    public NodeProjectsMojo(ProjectBuilder builder) {
        this.builder = builder;
    }

    @Override
    boolean applies() {
        return !RepositoryProjects.harnessParent(this.project());
    }

    @Override
    List<Findings> findings() {
        Path basedir = this.project().getBasedir().toPath();
        NodeProjects projects = NodeProjects.of(basedir, this.declared);
        List<Path> skipped = this.skipped(basedir);
        NodeProjectCoverage coverage = new NodeProjectCoverage(basedir, projects, skipped);
        Path root = this.repositoryRoot();
        FrontendCoverage frontend = new FrontendCoverage(
            new FrontendWalk(root, basedir, skipped), projects,
            () -> GeneratedDenialMojo.denied(
                root, RepositorySites.read(this.builder, this.session().getProjectBuildingRequest(), root),
                CommaList.entries(this.generated), CommaList.entries(this.unmanaged)
            ),
            HarnessFrontend.excused(this.project().getGroupId(), this.project().getArtifactId())
        );
        this.getLog().info(
            "Node projects: " + projects.projects().size() + " declared, " + coverage.found()
                + " package.json file(s) and " + frontend.inventoried() + " authored frontend file(s) found in "
                + this.project().getArtifactId()
        );
        return Stream.concat(coverage.findings().stream(), frontend.findings().stream()).toList();
    }

    // A child module walks its own directory in its own build, and the build directory holds output.
    private List<Path> skipped(Path basedir) {
        Stream<Path> modules = this.project().getModules().stream()
            .map(module -> basedir.resolve(module).normalize())
            .map(module -> Files.isRegularFile(module) ? module.resolveSibling("") : module);
        return Stream.concat(modules, Stream.of(Path.of(this.project().getBuild().getDirectory()))).toList();
    }
}
