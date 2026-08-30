package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.SpringSourceCheck;
import java.util.List;
import java.util.Objects;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;

/**
 * The Spring application class sits at the declared package root, and no bean method calls another.
 *
 * <p>Bound by {@code airness-parent-spring-boot} rather than by the parent above it, so the questions
 * are asked of a Spring Boot project and of nothing else.
 */
@Mojo(name = "spring-source", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class SpringSourceMojo extends AbstractGovernanceMojo {

    private static final String UNSET = "UNSET";

    /**
     * The package every class of this project lives under, which component scanning has to start at.
     */
    @Parameter(property = "airness.package.root", defaultValue = UNSET)
    private @Nullable String packageRoot;

    @Override
    boolean applies() {
        return this.hasModuleJava();
    }

    @Override
    List<Findings> findings() {
        SpringSourceCheck check = new SpringSourceCheck(
            this.repositoryRoot(), this.moduleSourceRoots(), this.packageRoot()
        );
        this.getLog().info("Spring source read " + check.scanned() + " Java source(s)");
        Scope.requireJavaSources(check.scanned(), this.moduleSourceRoots());
        return check.findings();
    }

    private String packageRoot() {
        return Objects.requireNonNull(this.packageRoot, "Maven did not inject airness.package.root");
    }
}
