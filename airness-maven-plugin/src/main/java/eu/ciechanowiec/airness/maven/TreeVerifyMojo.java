package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Reports committable files that changed while a verifying build ran.
 *
 * <p>What this knows is that the tree moved, and not what moved it. A build plugin writing to a
 * tracked file is the usual cause and the one worth refusing, and an author editing the tree while
 * the build ran is the other, so the verdict names both rather than asserting the first. A message
 * that named only the plugins sends whoever reads it to inspect a plugin that did nothing.
 */
@Mojo(name = "tree-verify", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class TreeVerifyMojo extends AbstractGovernanceMojo {

    private static final String HEADLINE = "Committable files changed during the build";

    private static final String MOVED
        = "The working tree content differs from the validate-phase snapshot, so either a build plugin "
            + "wrote to a tracked file or the tree was edited while the build ran";

    @Override
    boolean applies() {
        return OncePerSession.firstRun(
            this.session().getRepositorySession().getData(), this.getClass(), this.scope()
        );
    }

    @Override
    List<Findings> findings() {
        if (this.formatProfile()) {
            this.getLog().info("The format profile intentionally edits sources; tree comparison is disabled");
            return List.of(new Findings(HEADLINE, List.of()));
        }
        List<String> changed = TreeState.unchanged(
            this.session().getRepositorySession().getData(), this.repositoryRoot(), this.scope()
        )
            ? List.of() : List.of(MOVED);
        return List.of(new Findings(HEADLINE, changed));
    }

    private boolean formatProfile() {
        return this.project().getActiveProfiles().stream().anyMatch(profile -> "format".equals(profile.getId()));
    }

    private String scope() {
        return TreeState.scope(this.project().getFile().toPath());
    }
}
