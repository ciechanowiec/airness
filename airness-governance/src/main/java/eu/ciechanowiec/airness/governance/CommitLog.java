package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Reads the whole reachable history as {@link Commit} values. Merge commits keep their fixed header
 * form but remain in scope for typography and attribution checks.
 */
@UtilityClass
final class CommitLog {

    static List<Commit> commits(Path root) {
        return shas(root).stream()
            .map(sha -> commit(root, sha))
            .toList();
    }

    private static List<String> shas(Path root) {
        String output = GitPlumbing.run(root, List.of("rev-list", "HEAD"));
        return output.lines()
            .filter(line -> !line.isBlank())
            .toList();
    }

    private static Commit commit(Path root, String sha) {
        CommitMessage message = CommitMessages.parse(rawMessage(root, sha));
        DiffStat stat = DiffStats.parse(rawNumstat(root, sha));
        return new Commit(sha, message, stat);
    }

    private static String rawMessage(Path root, String sha) {
        return GitPlumbing.run(root, List.of("show", "--no-patch", "--format=%B", sha));
    }

    private static String rawNumstat(Path root, String sha) {
        return GitPlumbing.run(root, List.of("show", "--numstat", "--format=", sha));
    }
}
