package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.AssetSite;
import eu.ciechanowiec.airness.governance.ComplianceReport;
import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.NodeSuppressionCeilingCheck;
import eu.ciechanowiec.airness.governance.JavaSuppressionCeilingCheck;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.apache.maven.lifecycle.internal.LifecycleExecutionPlanCalculator;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.eclipse.aether.SessionData;
import org.jspecify.annotations.Nullable;

/**
 * Records that this module has finished every harness execution, and closes the build once every module has.
 *
 * <p>This is the last execution an Airness parent binds in {@code verify}, and it first proves that from the
 * order Maven planned: nothing runs after it in {@code verify} and no goal named on the command line runs after
 * it, or it refuses the build before closing. It then records the module as complete. The module whose record
 * completes the set runs the closing step, once per invocation, sequentially or as the last of a parallel
 * build: the final tree comparison against the tree the first module saw, the suppression ceiling across the
 * whole repository, and under the extended profile one SBOM and one compliance report for every module. A
 * module that fails never records itself, so a failed build never closes.
 */
@Mojo(
    name = "module-complete", defaultPhase = LifecyclePhase.VERIFY, requiresDependencyResolution = ResolutionScope.TEST,
    threadSafe = true
)
public final class ModuleCompleteMojo extends AbstractGovernanceMojo {

    @Parameter(property = "airness.compliance.output")
    private @Nullable String output;

    /**
     * Repository-relative paths of further files a generator owns, comma-separated, which the node suppression
     * ceiling leaves out beside the generated and pinned files the harness places.
     */
    @Parameter(property = "airness.generated.files")
    private @Nullable String generated;

    /**
     * Repository-relative paths this project has taken over from the harness, comma-separated.
     */
    @Parameter(property = "airness.assets.unmanaged")
    private @Nullable String unmanaged;

    private final LifecycleExecutionPlanCalculator plans;
    private final ProjectBuilder builder;

    /**
     * Uses Maven's own execution planner, and the builder that reads every module of the repository.
     *
     * @param plans   Maven's execution planner
     * @param builder the Maven project builder
     */
    @Inject
    public ModuleCompleteMojo(LifecycleExecutionPlanCalculator plans, ProjectBuilder builder) {
        this.plans = plans;
        this.builder = builder;
    }

    @Override
    List<String> refusals() {
        return CompletionOrderMojo.problems(this.plans, this.session(), this.project());
    }

    @Override
    List<Findings> findings() {
        List<MavenProject> required = ReportProjects.required(this.session().getProjects());
        Set<String> awaited = Closing.awaited(required);
        SessionData data = this.session().getRepositorySession().getData();
        if (!CompletionSession.complete(data, this.project().getId(), awaited)) {
            this.getLog().info("Module complete; closing waits for the other modules of this build");
            return List.of();
        }
        // Airness's own libraries inherit its aggregator, and its reactor builds them ahead of the parents.
        List<MavenProject> covered = RepositoryProjects.selfRepository(this.session().getTopLevelProject()) ? required
            : Closing.covered(
                required, project -> this.session().getProjectDependencyGraph().getUpstreamProjects(project, true)
            );
        List<String> uncovered = Closing.uncovered(required, covered);
        uncovered.forEach(line -> this.getLog().warn(line));
        this.getLog().info("Every module of this build is complete; closing runs here");
        return Stream.of(
            Stream.of(this.tree()), this.suppressions().stream(), this.report(covered, uncovered).stream()
        ).flatMap(stream -> stream).toList();
    }

    private Findings tree() {
        // The profile as the invocation selected it, on the top-level project: a module declaring a profile of the
        // same name cannot switch the comparison off for the whole repository.
        boolean format = ReportProjects.active(this.session().getTopLevelProject(), "format");
        if (format) {
            this.getLog().info("The format profile intentionally edits sources; the closing tree comparison is off");
            return Closing.tree(true);
        }
        return Closing.tree(
            TreeState.unchangedSinceStart(this.session().getRepositorySession().getData(), this.repositoryRoot())
        );
    }

    private List<Findings> suppressions() {
        List<Path> javaRoots = this.reactorSourceRoots();
        List<Path> nodeDirectories = this.session().getAllProjects().stream()
            .flatMap(project -> NpmEvidence.directories(project).stream()).toList();
        Stream<Findings> java = javaRoots.isEmpty() ? Stream.empty() : this.javaSuppressions(javaRoots).stream();
        Stream<Findings> node = nodeDirectories.isEmpty() ? Stream.empty()
            : this.nodeSuppressions(nodeDirectories).stream();
        return Stream.concat(java, node).toList();
    }

    private List<Findings> javaSuppressions(List<Path> javaRoots) {
        JavaSuppressionCeilingCheck check = new JavaSuppressionCeilingCheck(this.repositoryRoot(), javaRoots);
        this.getLog().info("Suppressions in force: " + check.count() + " of " + check.ceiling() + " allowed");
        Scope.requireJavaSources(check.scanned(), javaRoots);
        return check.findings();
    }

    // The node ceiling reads only code the project wrote: the files the harness pins and the files a layer or the
    // project declares generated are left out.
    private List<Findings> nodeSuppressions(List<Path> directories) {
        Path root = this.repositoryRoot();
        List<AssetSite> sites = RepositorySites.read(this.builder, this.session().getProjectBuildingRequest(), root);
        List<String> excluded = GeneratedDenialMojo.denied(
            root, sites, Sentinel.optional(this.generated), Sentinel.optional(this.unmanaged)
        );
        NodeSuppressionCeilingCheck check = new NodeSuppressionCeilingCheck(
            root, Closing.nodeSources(root, directories, excluded)
        );
        this.getLog().info("Node suppressions in force: " + check.count() + " of " + check.ceiling() + " allowed");
        return check.findings();
    }

    private List<Findings> report(Collection<MavenProject> covered, List<String> uncovered) {
        if (!ReportProjects.extended(this.project())) {
            return List.of();
        }
        ReportInputs inputs = new ReportInputs(this.session().getRequest().getStartTime().toInstant());
        List<ReportModule> modules = covered.stream().map(inputs::read).toList();
        ComplianceReport report = ReportGeneration.from(this.session(), this.project(), this.enforced()).qualified(
            uncovered
        ).write(
            modules, Optional.ofNullable(this.output)
        );
        this.getLog().info("Compliance report and SBOM written: " + report.fileStem() + ".html and .pdf");
        return List.of(new Findings("Dependency compliance report", report.inventory().failures()));
    }
}
