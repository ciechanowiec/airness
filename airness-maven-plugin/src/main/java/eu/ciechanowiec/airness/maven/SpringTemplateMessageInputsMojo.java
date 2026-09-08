package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.TemplateMessageInputs;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Supplies literal production-template references to the existing Spring startup listener.
 */
@Mojo(
    name = "spring-template-message-inputs", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true,
    requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public final class SpringTemplateMessageInputsMojo extends AbstractGovernanceMojo {

    @Override
    boolean applies() {
        return this.hasModuleJava();
    }

    @Override
    List<Findings> findings() {
        SpringTemplateMessageScope scope = new SpringTemplateMessageScope(
            this.repositoryRoot(), this.project(),
            this.session().getStartTime().toInstant().toEpochMilli()
        );
        TemplateMessageInputs inputs = scope.inputs();
        inputs.write(TemplateMessageInputs.beside(scope.evidence()));
        this.getLog().info("Prepared " + inputs.references().size() + " literal template message reference(s)");
        return List.of();
    }
}
