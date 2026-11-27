package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ActionRuntimeCheck;
import eu.ciechanowiec.airness.governance.Findings;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Every action a GitHub Actions workflow or composite action uses declares a Node runtime GitHub still supports,
 * read from the action's metadata at the ref it is pinned to, or runs no Node at all.
 *
 * <p>The metadata is read from GitHub, and that of a commit is kept under the local Maven repository, so a build
 * whose actions are pinned by commit reads GitHub only for a commit it has not read before.
 */
@Mojo(name = "action-runtimes", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class ActionRuntimesMojo extends AbstractRepositoryMojo {

    @Override
    List<Findings> findings() {
        GitHubActionMetadata source = GitHubActionMetadata.of(
            this.session().getRepositorySession().getLocalRepositoryManager().getRepository().getBasePath(),
            System.getenv()
        );
        ActionRuntimeCheck check = new ActionRuntimeCheck(this.repositoryRoot(), source);
        this.getLog().info(
            "Action runtimes read " + check.scanned() + " CI file(s) and the metadata of " + check.actions()
                + " action(s), kept per commit at " + source.cache()
        );
        return check.findings();
    }
}
