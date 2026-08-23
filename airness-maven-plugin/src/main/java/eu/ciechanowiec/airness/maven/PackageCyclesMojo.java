package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.PackageCycleCheck;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * The packages of the module depend on one another in one direction only.
 *
 * <p>This runs per module, because a cycle across two modules would need each of them to depend on the
 * other, and Maven refuses that before any goal of this plugin runs. The module is therefore the whole
 * of the scope in which a package cycle can exist.
 */
@Mojo(name = "package-cycles", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class PackageCyclesMojo extends AbstractGovernanceMojo {

    @Override
    boolean applies() {
        return !this.moduleSourceRoots().isEmpty();
    }

    @Override
    List<Findings> findings() {
        PackageCycleCheck check = new PackageCycleCheck(this.repositoryRoot(), this.moduleSourceRoots());
        this.getLog().info(
            "Package cycles read " + check.scanned() + " Java source(s) in " + check.packages() + " package(s)"
        );
        Scope.requireJavaSources(check.scanned(), this.moduleSourceRoots());
        return check.findings();
    }
}
