package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.CommitMessageCheck;
import eu.ciechanowiec.airness.governance.Findings;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * The message being written satisfies the policy, sized against what is actually staged.
 *
 * <p>This goal is bound to no phase. It is what a commit-message hook invokes, and it is the only check
 * here that reads a file the repository does not yet hold. Catching a bad message before it is written
 * is worth a separate entry point, because afterwards the only remedy is a rewrite of published history,
 * which is not a remedy at all.
 */
@Mojo(name = "commit-message", threadSafe = true)
public class CommitMessageMojo extends RepositoryMojo {

    /**
     * The file holding the message being written, which git hands a hook as its first argument.
     */
    @Parameter(property = "airness.commit.message.file", required = true)
    private String messageFile;

    @Override
    protected List<Findings> findings() {
        return new CommitMessageCheck(this.repositoryRoot(), Path.of(this.messageFile)).findings();
    }
}
