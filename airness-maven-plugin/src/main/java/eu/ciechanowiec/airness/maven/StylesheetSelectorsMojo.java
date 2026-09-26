package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.StylesheetSelectorCheck;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * A selector a module writes selects what it names, whatever the line it was wrapped onto.
 *
 * <p>This runs per module rather than once for the repository, for the same reason template-parse
 * does: the resources it reads are the module's own, and a module that declares no resource directory
 * is passed over.
 */
@Mojo(name = "stylesheet-selectors", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class StylesheetSelectorsMojo extends AbstractGovernanceMojo {

    @Override
    boolean applies() {
        return !this.moduleResourceRoots().isEmpty();
    }

    @Override
    List<Findings> findings() {
        StylesheetSelectorCheck check = new StylesheetSelectorCheck(
            this.repositoryRoot(), this.moduleResourceRoots()
        );
        this.getLog().info("Stylesheet selectors read " + check.scanned() + " stylesheet(s)");
        return check.findings();
    }
}
