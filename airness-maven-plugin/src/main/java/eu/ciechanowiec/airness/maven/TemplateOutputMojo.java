package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.TemplateOutputCheck;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * A module's markup writes nothing into a page unescaped, and has no expression read a second time as
 * an expression.
 *
 * <p>This runs per module rather than once for the repository, for the same reason template-parse
 * does: the resources it reads are the module's own, and a module that declares no resource directory
 * is passed over.
 */
@Mojo(name = "template-output", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class TemplateOutputMojo extends AbstractGovernanceMojo {

    @Override
    boolean applies() {
        return !this.moduleResourceRoots().isEmpty();
    }

    @Override
    List<Findings> findings() {
        TemplateOutputCheck check = new TemplateOutputCheck(
            this.repositoryRoot(), this.moduleResourceRoots()
        );
        this.getLog().info("Template output read " + check.scanned() + " markup resource(s)");
        return check.findings();
    }
}
