package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.AssertionCheck;
import eu.ciechanowiec.airness.governance.Findings;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Every test asserts an observable outcome, and no assertion is settled before the code under test
 * runs.
 *
 * <p>This runs per module rather than once for the repository, because the tests it reads are the
 * module's own and a reactor build should report a module's findings against that module.
 *
 * <p>A module with no test sources is passed over rather than failed. Whether a module is allowed to
 * carry no tests at all is a question the coverage floor answers, and answering it twice in two voices
 * is how the two come to disagree.
 */
@Mojo(name = "test-assertions", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class AssertionsMojo extends AbstractGovernanceMojo {

    @Override
    boolean applies() {
        return this.hasTestJava();
    }

    @Override
    List<Findings> findings() {
        AssertionCheck check = new AssertionCheck(this.repositoryRoot(), this.moduleTestSourceRoots());
        this.getLog().info("Test assertions read " + check.scanned() + " Java test source(s)");
        Scope.requireJavaSources(check.scanned(), this.moduleTestSourceRoots());
        return check.findings();
    }
}
