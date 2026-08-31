package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.TemplateLinkCheck;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * A link expression in a module's markup reaches for nothing the template engine refuses to read
 * inside one.
 *
 * <p>This runs per module rather than once for the repository, for the same reason template-parse
 * does: the resources it reads are the module's own, and a module that declares no resource directory
 * is passed over.
 */
@Mojo(name = "template-links", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class TemplateLinksMojo extends AbstractGovernanceMojo {

    @Override
    boolean applies() {
        return !this.moduleResourceRoots().isEmpty();
    }

    @Override
    List<Findings> findings() {
        TemplateLinkCheck check = new TemplateLinkCheck(this.repositoryRoot(), this.moduleResourceRoots());
        this.getLog().info("Template links read " + check.scanned() + " markup resource(s)");
        return check.findings();
    }
}
