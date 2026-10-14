package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ComplianceReport;
import eu.ciechanowiec.airness.governance.Findings;
import java.util.List;
import java.util.Optional;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jspecify.annotations.Nullable;

/**
 * Regenerates dependency evidence and writes an aggregate compliance report outside full verification.
 */
@Mojo(
    name = "compliance-report", aggregator = true, requiresDependencyResolution = ResolutionScope.TEST,
    threadSafe = true
)
@Execute(phase = LifecyclePhase.PACKAGE)
public final class ComplianceReportMojo extends AbstractGovernanceMojo {

    @Parameter(property = "airness.compliance.output")
    private @Nullable String output;

    @Override
    List<Findings> findings() {
        if (!ReportProjects.extended(this.project())) {
            throw new IllegalStateException("Compliance reporting needs Extended evidence; use -Pextended");
        }
        ReportInputs inputs = new ReportInputs(this.session().getRequest().getStartTime().toInstant());
        List<ReportModule> modules = ReportProjects.required(this.session().getProjects()).stream()
            .map(ReportProjects::executed).map(inputs::read).toList();
        ComplianceReport report = ReportGeneration.from(this.session(), this.project()).reportOnly().write(
            modules, Optional.ofNullable(this.output)
        );
        this.getLog().info("Compliance report written: " + report.fileStem() + ".html and .pdf");
        return List.of(new Findings("Dependency compliance report", report.inventory().failures()));
    }
}
