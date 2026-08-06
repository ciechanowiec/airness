package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Reports a plugin that changed committable files during a verifying build.
 */
@Mojo(name = "tree-verify", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class TreeVerifyMojo extends AbstractGovernanceMojo {

    @Override
    boolean applies() {
        return OncePerSession.firstRun(this.session(), this.getClass(), this.scope());
    }

    @Override
    List<Findings> findings() {
        if (this.formatProfile()) {
            this.getLog().info("The format profile intentionally edits sources; tree comparison is disabled");
            return List.of(new Findings("Build plugins changed committable files", List.of()));
        }
        List<String> changed = TreeState.unchanged(this.session(), this.repositoryRoot(), this.scope())
            ? List.of() : List.of("The working tree content differs from the validate-phase snapshot");
        return List.of(new Findings("Build plugins changed committable files", changed));
    }

    private boolean formatProfile() {
        return this.project().getActiveProfiles().stream().anyMatch(profile -> "format".equals(profile.getId()));
    }

    private String scope() {
        return TreeState.scope(this.project().getFile().toPath());
    }
}
