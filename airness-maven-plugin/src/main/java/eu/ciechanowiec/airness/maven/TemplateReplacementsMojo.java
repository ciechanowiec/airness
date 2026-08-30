package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.TemplateReplacementCheck;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * An element a module's markup replaces with a fragment carries nothing else the dialect reads.
 *
 * <p>This runs per module rather than once for the repository, for the same reason template-parse
 * does: the resources it reads are the module's own, and a module that declares no resource directory
 * is passed over.
 */
@Mojo(name = "template-replacements", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class TemplateReplacementsMojo extends AbstractGovernanceMojo {

    @Override
    boolean applies() {
        return !this.moduleResourceRoots().isEmpty();
    }

    @Override
    List<Findings> findings() {
        TemplateReplacementCheck check = new TemplateReplacementCheck(
            this.repositoryRoot(), this.moduleResourceRoots()
        );
        this.getLog().info("Template replacements read " + check.scanned() + " markup resource(s)");
        return check.findings();
    }
}
