package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.AxeAuditCheck;
import eu.ciechanowiec.airness.governance.Findings;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Reports a test that asks the accessibility rules a question of its own rather than the one the
 * harness ships.
 */
@Mojo(name = "web-accessibility", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class AxeAuditMojo extends AbstractGovernanceMojo {

    @Override
    boolean applies() {
        return this.hasTestJava();
    }

    @Override
    List<Findings> findings() {
        AxeAuditCheck check = new AxeAuditCheck(this.repositoryRoot(), this.moduleTestSourceRoots());
        this.getLog().info("Web accessibility read " + check.scanned() + " Java test source(s)");
        Scope.requireJavaSources(check.scanned(), this.moduleTestSourceRoots());
        return check.findings();
    }
}
