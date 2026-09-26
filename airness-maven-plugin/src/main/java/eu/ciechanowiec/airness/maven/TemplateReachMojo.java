package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.TemplateReachCheck;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Markup a module writes into a page drawn in a layout reaches a reader, or is not written.
 *
 * <p>This runs per module rather than once for the repository, for the same reason template-parse
 * does: the resources it reads are the module's own, and a module that declares no resource directory
 * is passed over.
 */
@Mojo(name = "template-reach", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class TemplateReachMojo extends AbstractGovernanceMojo {

    @Override
    boolean applies() {
        return !this.moduleResourceRoots().isEmpty();
    }

    @Override
    List<Findings> findings() {
        TemplateReachCheck check = new TemplateReachCheck(
            this.repositoryRoot(), this.moduleResourceRoots()
        );
        this.getLog().info("Template reach read " + check.scanned() + " markup resource(s)");
        return check.findings();
    }
}
