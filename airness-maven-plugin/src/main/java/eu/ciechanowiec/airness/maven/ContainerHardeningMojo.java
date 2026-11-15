package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ContainerHardeningCheck;
import eu.ciechanowiec.airness.governance.Findings;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * No compose service gains a privilege, shares a namespace of the host or publishes beyond loopback, and no compose
 * file or Dockerfile writes a credential into a container's definition.
 */
@Mojo(name = "container-hardening", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class ContainerHardeningMojo extends AbstractRepositoryMojo {

    @Override
    List<Findings> findings() {
        ContainerHardeningCheck check = new ContainerHardeningCheck(this.repositoryRoot());
        this.getLog().info("Container hardening read " + check.scanned());
        return check.findings();
    }
}
