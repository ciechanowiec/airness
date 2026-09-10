package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.StreamingTimeoutInputs;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Binds streaming timeout declarations to the production application's startup evidence.
 */
@Mojo(
    name = "spring-streaming-timeout-inputs", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true,
    requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public final class SpringStreamingTimeoutInputsMojo extends AbstractGovernanceMojo {

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
        inputs.write(StreamingTimeoutInputs.beside(scope.evidence()));
        this.getLog().info("Prepared streaming timeout inputs for " + inputs.applications().size() + " application(s)");
        return List.of();
    }
}
