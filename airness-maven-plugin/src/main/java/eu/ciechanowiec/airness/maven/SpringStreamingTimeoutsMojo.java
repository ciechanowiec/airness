package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.StreamingTimeoutCheck;
import eu.ciechanowiec.airness.governance.StreamingTimeoutInputs;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Binds streaming timeout declarations to the production application's startup evidence.
 */
@Mojo(
    name = "spring-streaming-timeouts", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, threadSafe = true,
    requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public final class SpringStreamingTimeoutsMojo extends AbstractGovernanceMojo {

    @Override
    boolean applies() {
        return this.hasModuleJava();
    }

    @Override
    List<Findings> findings() {
        StreamingTimeoutScope scope = new StreamingTimeoutScope(
            this.repositoryRoot(), this.project(),
            this.session().getAllProjects(), this.session().getStartTime().toInstant().toEpochMilli()
        );
        StreamingTimeoutInputs inputs = scope.inputs();
        StreamingTimeoutCheck check = new StreamingTimeoutCheck(
            inputs,
            StreamingTimeoutInputs.beside(scope.evidence()), scope.evidence()
        );
        this.getLog().info("Streaming timeouts: " + check.assessed() + " assessed context(s)");
        return check.findings();
    }
}
