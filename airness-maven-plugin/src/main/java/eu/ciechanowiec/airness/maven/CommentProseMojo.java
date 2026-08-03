package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.CommentProseCheck;
import eu.ciechanowiec.airness.governance.Findings;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * No comment joins two clauses with a semicolon, and no {@code @return} carries a full stop.
 *
 * <p>This runs per module rather than once for the repository, because the sources it reads are the
 * module's own and a reactor build should report a module's findings against that module.
 */
@Mojo(name = "comment-prose", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class CommentProseMojo extends AbstractGovernanceMojo {

    @Override
    boolean applies() {
        return !this.moduleSourceRoots().isEmpty();
    }

    @Override
    List<Findings> findings() {
        CommentProseCheck check = new CommentProseCheck(this.repositoryRoot(), this.moduleSourceRoots());
        this.getLog().info("Comment prose read " + check.scanned() + " Java source(s)");
        Scope.requireJavaSources(check.scanned(), this.moduleSourceRoots());
        return check.findings();
    }
}
