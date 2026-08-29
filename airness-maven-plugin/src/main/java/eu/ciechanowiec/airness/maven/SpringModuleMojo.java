package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.SpringModuleCheck;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * The Spring questions that need every source of the module read before any of them is judged.
 *
 * <p>Bound by {@code airness-parent-spring-boot} rather than by the parent above it, so a project that
 * is not a Spring Boot one is never asked.
 */
@Mojo(name = "spring-module", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class SpringModuleMojo extends AbstractGovernanceMojo {

    @Override
    boolean applies() {
        return this.hasModuleJava();
    }

    @Override
    List<Findings> findings() {
        SpringModuleCheck check = new SpringModuleCheck(
            this.repositoryRoot(), this.moduleSourceRoots(), this.moduleResourceRoots()
        );
        this.getLog().info(
            "Spring module read " + check.scanned() + " Java source(s) declaring " + check.types() + " type(s)"
        );
        Scope.requireJavaSources(check.scanned(), this.moduleSourceRoots());
        return check.findings();
    }
}
