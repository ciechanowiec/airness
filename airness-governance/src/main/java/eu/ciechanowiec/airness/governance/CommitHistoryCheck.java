package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.List;

/**
 * Every commit reachable from the history, back to and including the first commit, satisfies the
 * commit-message policy that {@link CommitMessageRules} states.
 *
 * <p>This walks the whole history, so it belongs to the slow verification profile, and it is worth
 * nothing over a shallow clone: the commits that were never fetched pass by not existing. The caller
 * refuses a shallow repository before running this, which is why {@link Repository} can say whether one
 * is shallow.
 */
public final class CommitHistoryCheck {

    private static final String HEADLINE = "Commit messages that break the policy";

    private final List<Commit> commits;

    /**
     * Reads the whole history.
     *
     * @param root the working tree root
     */
    public CommitHistoryCheck(Path root) {
        this.commits = CommitLog.commits(root);
    }

    /**
     * How many commits the check read, which a caller logs so the reach of a clean verdict is on the
     * record.
     *
     * @return the number of commits in scope
     */
    public int scanned() {
        return this.commits.size();
    }

    /**
     * Every policy violation, each naming the commit it belongs to.
     *
     * @return the single verdict this check produces
     */
    public List<Findings> findings() {
        return List.of(
            new Findings(HEADLINE, this.commits.stream().flatMap(commit -> annotate(commit).stream()).toList())
        );
    }

    private static List<String> annotate(Commit commit) {
        return CommitMessageRules.validate(commit.message(), commit.stat(), commit.merge()).stream()
            .map(violation -> commit.sha() + ": " + violation)
            .toList();
    }
}
