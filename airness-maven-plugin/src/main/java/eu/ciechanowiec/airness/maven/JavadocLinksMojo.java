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
public final class JavadocLinksMojo extends AbstractGovernanceMojo {

    @Override
    boolean applies() {
        return !this.moduleSourceRoots().isEmpty();
    }

    @Override
    List<Findings> findings() {
        JavadocLinkCheck check = new JavadocLinkCheck(this.repositoryRoot(), this.moduleSourceRoots());
        this.getLog().info("Javadoc links read " + check.scanned() + " Java source(s)");
        Scope.requireJavaSources(check.scanned(), this.moduleSourceRoots());
        return check.findings();
    }
}
