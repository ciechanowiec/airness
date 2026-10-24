package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.EnvironmentCheck;
import eu.ciechanowiec.airness.governance.Findings;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Every environment variable a compose file needs is documented in the example file and supplied by
 * every workflow that runs verification, and every variable a Next.js build inlines is declared by the
 * Dockerfile that builds it.
 */
@Mojo(name = "environment", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class EnvironmentMojo extends AbstractRepositoryMojo {

    @Override
    List<Findings> findings() {
        return new EnvironmentCheck(this.repositoryRoot(), PackageManifests::in).findings();
    }
}
