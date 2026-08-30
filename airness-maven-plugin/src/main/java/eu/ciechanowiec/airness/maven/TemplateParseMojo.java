package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.TemplateParseCheck;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Every markup resource a module ships can be read by the parser the template engines use.
 *
 * <p>This runs per module rather than once for the repository, because the resources it reads are the
 * module's own. A module that declares no resource directory is passed over, and one that declares
 * some but ships no markup reads nothing and says so, which is an ordinary state rather than a
 * defect: a library module of a web project holds no templates.
 */
@Mojo(name = "template-parse", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class TemplateParseMojo extends AbstractGovernanceMojo {

    @Override
    boolean applies() {
        return !this.moduleResourceRoots().isEmpty();
    }

    @Override
    List<Findings> findings() {
        TemplateParseCheck check = new TemplateParseCheck(this.repositoryRoot(), this.moduleResourceRoots());
        this.getLog().info("Template parse read " + check.scanned() + " markup resource(s)");
        return check.findings();
    }
}
