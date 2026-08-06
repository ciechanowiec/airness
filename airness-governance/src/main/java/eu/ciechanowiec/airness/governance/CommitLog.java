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
        return entries(root).stream()
            .map(entry -> commit(root, entry))
            .toList();
    }

    private static List<HistoryEntry> entries(Path root) {
        String output = GitPlumbing.run(root, List.of("rev-list", "--parents", "HEAD"));
        return output.lines()
            .filter(line -> !line.isBlank())
            .map(CommitLog::entry)
            .toList();
    }

    private static HistoryEntry entry(String line) {
        String[] fields = line.strip().split("\\s+", -1);
        return new HistoryEntry(fields[0], fields.length > 2);
    }

    private static Commit commit(Path root, HistoryEntry entry) {
        CommitMessage message = CommitMessages.parse(rawMessage(root, entry.sha()));
        DiffStat stat = DiffStats.parse(rawNumstat(root, entry.sha()));
        return new Commit(entry.sha(), message, stat, entry.merge());
    }

    private static String rawMessage(Path root, String sha) {
        return GitPlumbing.run(root, List.of("show", "--no-patch", "--format=%B", sha));
    }

    private static String rawNumstat(Path root, String sha) {
        return GitPlumbing.run(root, List.of("show", "--numstat", "--format=", sha));
    }

    private record HistoryEntry(String sha, boolean merge) {
    }
}
