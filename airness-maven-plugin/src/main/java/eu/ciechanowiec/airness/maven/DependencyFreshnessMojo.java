package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ContainerFreshnessCheck;
import eu.ciechanowiec.airness.governance.DependencyFreshnessCheck;
import eu.ciechanowiec.airness.governance.Findings;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * No declared dependency, plugin, or parent trails the latest stable release by two major versions or
 * more, and every Airness-owned container image is checked for available stable tags and digest drift.
 *
 * <p>This runs once over every pom in the reactor and its complete parent chain. Reading raw poms
 * reaches management, reporting, plugin classpaths, annotation processors, and inactive profiles
 * instead of limiting the verdict to coordinates active in the effective model. It reports every
 * stable update, fails only Maven coordinates at the major-version bound, and reports container updates
 * without a failure threshold. It fails rather than passes when a registry cannot be read.
 */
@Mojo(name = "dependency-freshness", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class DependencyFreshnessMojo extends AbstractGovernanceMojo {

    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";

    @Override
    List<Findings> findings() {
        DependencyFreshnessCheck dependencies = new DependencyFreshnessCheck(
            VersionCoordinates.from(this.session()), MAVEN_CENTRAL
        );
        this.getLog().info(
            "Version checking asked Maven Central about " + dependencies.scanned() + " coordinate(s)"
        );
        this.reportUpdates(dependencies);
        ContainerFreshnessCheck containers = new ContainerFreshnessCheck(
            VersionImages.from(this.session())
        );
        this.reportContainers(containers);
        return dependencies.findings();
    }

    @Override
    boolean applies() {
        return OncePerSession.firstRun(
            this.session().getRepositorySession().getData(), this.getClass()
        );
    }

    private void reportUpdates(DependencyFreshnessCheck check) {
        if (check.updates().isEmpty()) {
            this.getLog().info("Every checked dependency, plugin, and parent is current");
        } else {
            this.getLog().info("The following stable version updates are available:");
            check.updates().forEach(update -> this.getLog().info("  " + update.report()));
        }
        if (!check.refusedLatest().isEmpty()) {
            this.getLog().info(
                "The following newest releases are refused by name, so the freshness bound stops short:"
            );
            check.refusedLatest().forEach(note -> this.getLog().info("  " + note));
        }
    }

    private void reportContainers(ContainerFreshnessCheck check) {
        this.getLog().info(
            "Version checking asked Docker Hub about " + check.scanned() + " container image(s)"
        );
        if (check.updates().isEmpty()) {
            this.getLog().info("Every checked container image tag is current");
        } else {
            this.getLog().info("The following stable container image updates are available:");
            check.updates().forEach(update -> this.getLog().info("  " + update));
        }
        if (!check.drifts().isEmpty()) {
            this.getLog().info("The following pinned container tags now resolve to different digests:");
            check.drifts().forEach(drift -> this.getLog().info("  " + drift));
        }
    }
}
