package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.TemplateScopeCheck;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * A fragment binds every name it reads, or reads none that a request for it alone would leave unbound.
 *
 * <p>This runs per module rather than once for the repository, for the same reason template-parse
 * does: the resources it reads are the module's own, and a module that declares no resource directory
 * is passed over.
 */
@Mojo(name = "template-scope", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class TemplateScopeMojo extends AbstractGovernanceMojo {

    @Override
    boolean applies() {
        return !this.moduleResourceRoots().isEmpty();
    }

    @Override
    List<Findings> findings() {
        TemplateScopeCheck check = new TemplateScopeCheck(
            this.repositoryRoot(), this.moduleResourceRoots()
        );
        this.getLog().info("Template scope read " + check.scanned() + " markup resource(s)");
        return check.findings();
    }
}
