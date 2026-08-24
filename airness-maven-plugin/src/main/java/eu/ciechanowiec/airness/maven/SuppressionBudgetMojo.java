package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.SuppressionBudgetCheck;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * The repository holds no more suppressions than the declared rate allows over the code it has.
 *
 * <p>This runs once for the whole reactor rather than once per module. A per-module ceiling would carry
 * its own smallest allowance into every module, so splitting a project into more modules would raise
 * the total the repository may hold without a line of it changing, and the number would stop describing
 * the repository.
 *
 * <p>The count is logged on every run, clean or not. A ceiling nobody sees approaching is a ceiling
 * that arrives as a surprise on the change that happens to cross it.
 */
@Mojo(name = "suppression-budget", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class SuppressionBudgetMojo extends AbstractGovernanceMojo {

    /*
     * Four and a half suppressions per thousand lines of Java source, with the smallest ceiling of
     * five holding underneath the rate. The rate leaves room for justified analyzer boundaries that
     * code cannot remove while keeping suppressions sparse as a project grows. The ceiling still
     * arrives long before a project could suppress its way to a clean build.
     *
     * Deliberately not a parameter. A ceiling a project can raise is a ceiling that gets raised on the
     * change that would otherwise have failed, which is the one moment the ceiling exists to catch.
     */
    private static final double RATE = 4.5;

    @Override
    boolean applies() {
        return !this.reactorSourceRoots().isEmpty() && OncePerSession.firstRun(
            this.session().getRepositorySession().getData(), this.getClass(), this.repositoryRoot().toString()
        );
    }

    @Override
    List<Findings> findings() {
        SuppressionBudgetCheck check = new SuppressionBudgetCheck(
            this.repositoryRoot(), this.reactorSourceRoots(), RATE
        );
        this.getLog().info(
            "Suppressions in force: " + check.count() + " of " + check.ceiling() + " allowed"
        );
        Scope.requireJavaSources(check.scanned(), this.reactorSourceRoots());
        return check.findings();
    }
}
