package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.JavadocLinkCheck;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Javadoc links every type name it uses that the file it sits in could resolve, leaving {@code @code}
 * for the things a link cannot reach.
 */
@Mojo(name = "javadoc-links", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class JavadocLinksMojo extends GovernanceMojo {

    @Override
    protected boolean applies() {
        return !this.moduleSourceRoots().isEmpty();
    }

    @Override
    protected List<Findings> findings() {
        JavadocLinkCheck check = new JavadocLinkCheck(this.repositoryRoot(), this.moduleSourceRoots());
        this.getLog().info("Javadoc links read " + check.scanned() + " Java source(s)");
        Scope.requireNonEmpty(check.scanned(), "Java sources", this.moduleSourceRoots());
        return check.findings();
    }
}
