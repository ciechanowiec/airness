package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.List;

/**
 * The pending commit message satisfies the policy that {@link CommitMessageRules} states, sized against
 * what is actually staged so the body requirement tracks the change rather than a guess.
 *
 * <p>This is what a commit-message hook runs, and it is the only check here that reads a file the
 * repository does not yet hold. Catching a bad message before it is written is worth a separate entry
 * point, because once it is written the only remedy left is a rewrite of published history, which is
 * not a remedy at all.
 */
public final class CommitMessageCheck {

    private static final String HEADLINE = "The commit message violates policy";

    private final CommitMessage message;
    private final DiffStat stat;

    /**
     * Reads the pending message and sizes the staged change.
     *
     * @param root        the working tree root
     * @param messageFile the file holding the message being written
     */
    public CommitMessageCheck(Path root, Path messageFile) {
        this.message = CommitMessages.parse(read(messageFile));
        this.stat = DiffStats.parse(GitPlumbing.run(root, List.of("diff", "--cached", "--numstat")));
    }

    /**
     * Every way the pending message breaks the policy.
     *
     * @return the single verdict this check produces
     */
    public List<Findings> findings() {
        return List.of(new Findings(HEADLINE, CommitMessageRules.validate(this.message, this.stat)));
    }

    private static String read(Path file) {
        return Repository.readText(file).orElseThrow(
            () -> new IllegalStateException("Could not read the commit message file " + file)
        );
    }
}
