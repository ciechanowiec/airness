package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ComposeIsolationCheck;
import eu.ciechanowiec.airness.governance.Findings;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * No compose project publishes on a host port it fixes or fixes a container name, so a second checkout of the
 * project, or another project, starts beside it on one machine.
 */
@Mojo(name = "compose-isolation", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class ComposeIsolationMojo extends AbstractRepositoryMojo {

    @Override
    List<Findings> findings() {
        ComposeIsolationCheck check = new ComposeIsolationCheck(this.repositoryRoot());
        this.getLog().info("Compose isolation read " + check.scanned() + " compose project(s)");
        return check.findings();
    }
}
