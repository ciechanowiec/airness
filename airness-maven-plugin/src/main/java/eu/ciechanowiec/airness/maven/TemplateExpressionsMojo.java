package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.TemplateExpressionCheck;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * A call in a module's markup is written where the template engine will evaluate one.
 *
 * <p>This runs per module rather than once for the repository, for the same reason template-parse
 * does: the resources it reads are the module's own, and a module that declares no resource directory
 * is passed over.
 */
@Mojo(name = "template-expressions", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class TemplateExpressionsMojo extends AbstractGovernanceMojo {

    @Override
    boolean applies() {
        return !this.moduleResourceRoots().isEmpty();
    }

    @Override
    List<Findings> findings() {
        TemplateExpressionCheck check = new TemplateExpressionCheck(
            this.repositoryRoot(), this.moduleResourceRoots()
        );
        this.getLog().info("Template expressions read " + check.scanned() + " markup resource(s)");
        return check.findings();
    }
}
