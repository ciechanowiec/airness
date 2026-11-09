package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.RunnerImageCheck;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Every hosted Ubuntu runner a GitHub Actions workflow, a composite action or an Azure Pipelines definition
 * selects runs the current Ubuntu long-term-support release, named by its release rather than by an alias.
 */
@Mojo(name = "runner-images", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class RunnerImagesMojo extends AbstractRepositoryMojo {

    @Override
    List<Findings> findings() {
        RunnerImageCheck check = new RunnerImageCheck(this.repositoryRoot());
        this.getLog().info("Runner images read " + check.scanned() + " CI file(s)");
        return check.findings();
    }
}
