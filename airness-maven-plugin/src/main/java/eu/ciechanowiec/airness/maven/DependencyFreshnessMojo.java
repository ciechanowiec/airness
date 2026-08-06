package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.DependencyFreshnessCheck;
import eu.ciechanowiec.airness.governance.Findings;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * No declared dependency, plugin, or parent trails the latest stable release by two major versions or
 * more.
 *
 * <p>This runs once over every pom in the reactor and its complete parent chain. Reading raw poms
 * reaches management, reporting, plugin classpaths, annotation processors, and inactive profiles
 * instead of limiting the verdict to coordinates active in the effective model. It reports every
 * stable update, fails only at the major-version bound, and fails rather than passes when the registry
 * cannot be read.
 */
@Mojo(name = "dependency-freshness", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class DependencyFreshnessMojo extends AbstractGovernanceMojo {

    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";

    @Override
    List<Findings> findings() {
        DependencyFreshnessCheck check = new DependencyFreshnessCheck(
            VersionCoordinates.from(this.session()), MAVEN_CENTRAL
        );
        this.getLog().info(
            "Version checking asked Maven Central about " + check.scanned() + " coordinate(s)"
        );
        this.reportUpdates(check);
        return check.findings();
    }

    @Override
    boolean applies() {
        return OncePerSession.firstRun(this.session(), this.getClass());
    }

    private void reportUpdates(DependencyFreshnessCheck check) {
        if (check.updates().isEmpty()) {
            this.getLog().info("Every checked dependency, plugin, and parent is current");
        } else {
            this.getLog().info("The following stable version updates are available:");
            check.updates().forEach(update -> this.getLog().info("  " + update.report()));
        }
    }
}
