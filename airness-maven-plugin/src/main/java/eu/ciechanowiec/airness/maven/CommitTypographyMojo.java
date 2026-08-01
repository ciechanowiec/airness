package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.CommitTypographyCheck;
import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.Repository;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Every commit message uses plain ASCII typography.
 *
 * <p>A commit message is not a tracked file, so the tree scan cannot reach it, and it is the one piece
 * of prose a repository can never correct in place. That is why it gets a goal of its own rather than
 * being folded into {@code typography}.
 */
@Mojo(name = "commit-typography", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class CommitTypographyMojo extends RepositoryMojo {

    @Override
    protected List<Findings> findings() {
        Path root = this.repositoryRoot();
        return Repository.hasCommits(root) ? this.checked(root) : this.unborn();
    }

    private List<Findings> checked(Path root) {
        CommitTypographyCheck check = new CommitTypographyCheck(root);
        this.getLog().info("Commit typography read " + check.scanned() + " commit(s)");
        return check.findings();
    }

    private List<Findings> unborn() {
        this.getLog().info("The repository has no commits yet, so there is no message to read");
        return List.of();
    }
}
