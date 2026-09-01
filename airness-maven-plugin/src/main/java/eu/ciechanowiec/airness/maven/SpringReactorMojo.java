package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.SpringReactorCheck;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * The Spring questions a single module cannot answer, asked once for the whole build.
 *
 * <p>A second application class is one shape this exists for: each module holding one is correct on its
 * own, and only the build as a whole shows that two of them exist. Feature use and activation are the
 * inverse shape, because a library uses an annotation that configuration in the application module
 * enables. Running either question per module gives the wrong verdict and repeats it across the reactor.
 */
@Mojo(name = "spring-reactor", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class SpringReactorMojo extends AbstractRepositoryMojo {

    @Override
    List<Findings> findings() {
        SpringReactorCheck check = new SpringReactorCheck(
            this.repositoryRoot(), this.reactorSourceRoots()
        );
        this.getLog().info("Spring reactor read " + check.scanned() + " Java source(s)");
        Scope.requireJavaSources(check.scanned(), this.reactorSourceRoots());
        return check.findings();
    }
}
