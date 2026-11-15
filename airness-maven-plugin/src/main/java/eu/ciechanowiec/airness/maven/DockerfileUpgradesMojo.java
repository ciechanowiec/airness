package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.DockerfileUpgradeCheck;
import eu.ciechanowiec.airness.governance.Findings;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * No Dockerfile upgrades the system packages its base image holds, so the repair for a package a shipped image
 * holds vulnerable is a newer release of the base, which the compliance report names.
 */
@Mojo(name = "dockerfile-upgrades", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class DockerfileUpgradesMojo extends AbstractRepositoryMojo {

    @Override
    List<Findings> findings() {
        DockerfileUpgradeCheck check = new DockerfileUpgradeCheck(this.repositoryRoot());
        this.getLog().info("Dockerfile upgrades read " + check.scanned() + " Dockerfile(s)");
        return check.findings();
    }
}
