package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.CommitIdentityCheck;
import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.Repository;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Every author email and every committer email in the reachable history stands for exactly one name.
 *
 * <p>This reads the whole history, so it belongs to the slow verification profile, and it is worth
 * nothing over a truncated clone. The {@code require-full-history} goal refuses that case at
 * {@code validate}, before this one gets the chance to pass by reading a handful of commits.
 */
@Mojo(name = "commit-identity", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class CommitIdentityMojo extends AbstractRepositoryMojo {

    @Override
    List<Findings> findings() {
        Path root = this.repositoryRoot();
        return Repository.hasCommits(root) ? this.checked(root) : this.unborn();
    }

    private List<Findings> checked(Path root) {
        CommitIdentityCheck check = new CommitIdentityCheck(root);
        this.getLog().info("Commit identity read " + check.scanned() + " commit(s)");
        return check.findings();
    }

    private List<Findings> unborn() {
        this.getLog().info("The repository has no commits yet, so there is no identity to read");
        return List.of();
    }
}
