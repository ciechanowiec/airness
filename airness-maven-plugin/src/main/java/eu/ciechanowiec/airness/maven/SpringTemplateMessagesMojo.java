package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.TemplateMessageInputs;
import eu.ciechanowiec.airness.governance.TemplateMessagesCheck;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Requires current-run evidence that literal template names resolve from base messages.
 */
@Mojo(
    name = "spring-template-messages", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, threadSafe = true,
    requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public final class SpringTemplateMessagesMojo extends AbstractGovernanceMojo {

    @Override
    boolean applies() {
        return this.hasProductionJava() && SpringTemplateMessageScope.includesThymeleaf(this.project());
    }

    @Override
    List<Findings> findings() {
        SpringTemplateMessageScope scope = new SpringTemplateMessageScope(
            this.repositoryRoot(), this.project(),
            this.session().getStartTime().toInstant().toEpochMilli()
        );
        TemplateMessageInputs inputs = scope.inputs();
        TemplateMessagesCheck check = new TemplateMessagesCheck(
            inputs, TemplateMessageInputs.beside(scope.evidence()),
            scope.evidence()
        );
        this.getLog().info(
            "Template messages: " + inputs.references().size() + " reference(s), " + check.checked()
                + " checked lookup(s), " + check.unassessed().size() + " unassessed scope(s)"
        );
        check.unassessed().forEach(reason -> this.getLog().info("Unassessed template messages: " + reason));
        return check.findings();
    }
}
