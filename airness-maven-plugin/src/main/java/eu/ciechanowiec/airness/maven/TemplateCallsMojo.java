package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.TemplateCallCheck;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * A fragment call in a module's markup reaches a fragment the module declares, and hands it the
 * argument list that declaration takes.
 *
 * <p>This runs per module rather than once for the repository, for the same reason template-parse
 * does: the resources it reads are the module's own, and a module that declares no resource directory
 * is passed over. The module is also the scope the question belongs to, since a fragment and the
 * document calling it are shipped together or neither is shipped at all.
 */
@Mojo(name = "template-calls", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class TemplateCallsMojo extends AbstractGovernanceMojo {

    @Override
    boolean applies() {
        return !this.moduleResourceRoots().isEmpty();
    }

    @Override
    List<Findings> findings() {
        TemplateCallCheck check = new TemplateCallCheck(this.repositoryRoot(), this.moduleResourceRoots());
        this.getLog().info("Template calls read " + check.scanned() + " markup resource(s)");
        return check.findings();
    }
}
