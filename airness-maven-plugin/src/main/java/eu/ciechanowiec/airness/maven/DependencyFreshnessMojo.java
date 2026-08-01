package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.DependencyFreshnessCheck;
import eu.ciechanowiec.airness.governance.Findings;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * No declared dependency trails the latest stable release by two major versions or more.
 *
 * <p>This runs per module, because a declared dependency belongs to the pom that declares it. It reaches
 * the network, which is why it belongs to the slow verification profile, and it fails rather than passes
 * when the registry cannot be read: a dependency whose latest release is unknown is not a dependency
 * known to be current, so an outage that read as a green build would be the worst of both.
 */
@Mojo(name = "dependency-freshness", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class DependencyFreshnessMojo extends GovernanceMojo {

    /**
     * The base URL of the registry asked about each dependency.
     *
     * <p>A parameter rather than a constant, so a project behind a mirror can point it elsewhere and so
     * the fail-closed behaviour above can be watched happening.
     */
    @Parameter(property = "airness.registry", defaultValue = DependencyFreshnessCheck.CENTRAL)
    private String registry;

    @Override
    protected List<Findings> findings() {
        DependencyFreshnessCheck check = new DependencyFreshnessCheck(
            this.project().getFile().toPath(), this.registry
        );
        this.getLog().info(
            "Dependency freshness asked " + this.registry + " about " + check.scanned() + " dependency(ies)"
        );
        return check.findings();
    }
}
