package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.TemplateFragmentCheck;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Every fragment a module's markup declares takes no more arguments than a callable may.
 *
 * <p>This runs per module rather than once for the repository, for the same reason template-parse
 * does: the resources it reads are the module's own, and a module that declares no resource directory
 * is passed over.
 */
@Mojo(name = "template-fragments", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class TemplateFragmentsMojo extends AbstractGovernanceMojo {

    @Override
    boolean applies() {
        return !this.moduleResourceRoots().isEmpty();
    }

    @Override
    List<Findings> findings() {
        TemplateFragmentCheck check = new TemplateFragmentCheck(
            this.repositoryRoot(), this.moduleResourceRoots()
        );
        this.getLog().info("Template fragments read " + check.scanned() + " markup resource(s)");
        return check.findings();
    }
}
