package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.List;

/**
 * Every commit message in the whole history uses only plain ASCII typography, by the same
 * {@link TypographyRules} that {@link TypographyScanCheck} applies to the tree.
 *
 * <p>A commit message is not a tracked file, so the tree scan cannot reach it, and it is the one piece
 * of prose a repository can never correct in place. That is why it gets its own pass rather than being
 * folded into the tree scan.
 */
public final class CommitTypographyCheck {

    private static final String HEADLINE = "Banned typography in commit messages";
    // Git stores a commit message with this separator whatever platform wrote it, and the attribution scan
    // rejoins the same two parts the same way. Asking the running platform instead would have one message
    // read as two different texts depending on who ran the build.
    private static final String NEWLINE = "\n";

    private final List<Commit> commits;

    /**
     * Reads the whole history.
     *
     * @param root the working tree root
     */
    public CommitTypographyCheck(Path root) {
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
     * Every banned code point, each naming the commit and the code point.
     *
     * @return the single verdict this check produces
     */
    public List<Findings> findings() {
        return List.of(
            new Findings(HEADLINE, this.commits.stream().flatMap(commit -> violations(commit).stream()).toList())
        );
    }

    private static List<String> violations(Commit commit) {
        String text = commit.message().header() + NEWLINE + commit.message().body();
        return TypographyRules.findViolations(text).stream()
            .map(violation -> "%s: U+%04X".formatted(commit.sha(), violation.codePoint()))
            .toList();
    }
}
