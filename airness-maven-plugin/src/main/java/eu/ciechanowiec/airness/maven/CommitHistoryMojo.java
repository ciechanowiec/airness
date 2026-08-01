package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.CommitHistoryCheck;
import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.Repository;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Every commit message in the reachable history satisfies the policy.
 *
 * <p>This reads the whole history, so it belongs to the slow verification profile, and it is worth
 * nothing over a truncated clone. The {@code require-full-history} goal refuses that case at
 * {@code validate}, before this one gets the chance to pass by reading a handful of commits.
 */
@Mojo(name = "commit-history", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class CommitHistoryMojo extends RepositoryMojo {

    @Override
    protected List<Findings> findings() {
        Path root = this.repositoryRoot();
        return Repository.hasCommits(root) ? this.checked(root) : this.unborn();
    }

    private List<Findings> checked(Path root) {
        CommitHistoryCheck check = new CommitHistoryCheck(root);
        this.getLog().info("Commit history read " + check.scanned() + " commit(s)");
        return check.findings();
    }

    private List<Findings> unborn() {
        this.getLog().info("The repository has no commits yet, so there is no history to read");
        return List.of();
    }
}
