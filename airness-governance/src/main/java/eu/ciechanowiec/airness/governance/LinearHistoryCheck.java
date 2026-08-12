package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.List;

/**
 * Every commit reachable from the history has at most one parent, so the history is linear and carries no
 * merge commit, whichever branch recorded it. A branch takes the work of another by a rebase or a
 * cherry-pick instead.
 *
 * <p>The reading is topological rather than textual: a merge is what git recorded as a second parent, not
 * what a header happens to say. An ordinary commit whose subject opens with the word merge is not a merge,
 * and it answers to {@link CommitMessageRules} like any other.
 *
 * <p>This walks the whole history, so it belongs to the slow verification profile, and it is worth nothing
 * over a shallow clone: the merges that were never fetched pass by not existing. The caller refuses a
 * shallow repository before running this, which is why {@link Repository} can say whether one is shallow.
 */
public final class LinearHistoryCheck {

    private static final String HEADLINE = "Merge commits in the history";

    private final List<Commit> commits;

    /**
     * Reads the whole history.
     *
     * @param root the working tree root
     */
    public LinearHistoryCheck(Path root) {
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
     * Every merge commit, each naming the commit it belongs to.
     *
     * @return the single verdict this check produces
     */
    public List<Findings> findings() {
        return List.of(
            new Findings(
                HEADLINE,
                this.commits.stream().filter(Commit::merge).map(LinearHistoryCheck::offence).toList()
            )
        );
    }

    private static String offence(Commit commit) {
        return commit.sha() + ": a merge commit, where a rebase or a cherry-pick would record one parent";
    }
}
