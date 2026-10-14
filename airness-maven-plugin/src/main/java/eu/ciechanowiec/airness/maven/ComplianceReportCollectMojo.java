package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ComplianceReport;
import eu.ciechanowiec.airness.governance.Findings;
import java.util.List;
import java.util.Optional;
import org.apache.maven.execution.BuildSuccess;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

/**
 * Collects current module evidence and publishes one pair when the reactor's evidence is complete.
 */
@Mojo(
    name = "compliance-report-collect", defaultPhase = LifecyclePhase.VERIFY,
    requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true
)
public final class ComplianceReportCollectMojo extends AbstractGovernanceMojo {

    @Parameter(property = "airness.compliance.output")
    private @Nullable String output;

    @Override
    List<Findings> findings() {
        ReportInputs inputs = new ReportInputs(this.session().getRequest().getStartTime().toInstant());
        List<MavenProject> completed = this.session().getProjects().stream().filter(
            project -> this.session().getResult().getBuildSummary(
                project
            ) instanceof BuildSuccess
        ).toList();
        boolean self = RepositoryProjects.selfBuild(this.session().getTopLevelProject(), this.project());
        List<ReportModule> incoming = ReportProjects.ready(completed, this.project(), self).stream()
            .map(inputs::read).toList();
        List<String> required = ReportProjects.required(this.session().getProjects()).stream()
            .map(MavenProject::getId).toList();
        Optional<List<ReportModule>> complete = ReportSession.collect(
            this.session().getRepositorySession().getData(), incoming, required
        );
        return complete.map(this::write).orElseGet(List::of);
    }

    private List<Findings> write(List<ReportModule> modules) {
        ComplianceReport report = ReportGeneration.from(this.session(), this.project()).write(
            modules, Optional.ofNullable(this.output)
        );
        this.getLog().info("Compliance report written: " + report.fileStem() + ".html and .pdf");
        return List.of(new Findings("Dependency compliance report", report.inventory().failures()));
    }
}
