package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;

/**
 * Reads the whole reachable history as {@link Commit} values. Each one carries whether git recorded it
 * with more than one parent, which is how {@link LinearHistoryCheck} recognizes a merge and why the shape
 * rules stay silent on one. Every commit stays in scope for the typography and attribution checks.
 *
 * <p>The history is read by one {@code git log} rather than by a command per commit. A command per commit
 * costs a process start for every row of the history, so the time of a check grew with the age of the
 * repository rather than with what it reads. One stream reads the same facts in a single pass. Each
 * commit is framed by NUL bytes, which git refuses inside a commit message, so a message can hold any
 * other text without being misread as the start of the next commit. A merge is measured against its
 * first parent, as {@code git show --numstat} measures it.
 */
@UtilityClass
final class CommitLog {

    private static final String SEPARATOR = Character.toString(0);
    private static final List<String> COMMAND = List.of(
        "log", "--no-color", "--no-show-signature", "--diff-merges=first-parent", "--numstat",
        "--format=%x00%H %P%n%B%x00", "HEAD"
    );
    private static final int FIELDS_PER_COMMIT = 2;

    static List<Commit> commits(Path root) {
        return parse(GitPlumbing.run(root, COMMAND));
    }

    /**
     * Splits the output of the history command into commits.
     *
     * <p>The output opens with a separator and then alternates between a record, the hash, the parents
     * and the message, and the numstat rows of the same commit, so the first field is empty and every
     * commit owns the two fields after it.
     *
     * @param output the standard output of the history command
     * @return one commit per record, in the order git listed them
     */
    static List<Commit> parse(String output) {
        List<String> fields = Arrays.asList(output.split(SEPARATOR, -1));
        int count = (fields.size() - 1) / FIELDS_PER_COMMIT;
        return IntStream.range(0, count)
            .map(index -> 1 + index * FIELDS_PER_COMMIT)
            .mapToObj(first -> commit(fields.get(first), fields.get(first + 1)))
            .toList();
    }

    private static Commit commit(String entry, String numstat) {
        int newline = entry.indexOf('\n');
        String identity = newline < 0 ? entry : entry.substring(0, newline);
        String message = newline < 0 ? "" : entry.substring(newline + 1);
        String[] hashes = identity.strip().split("\\s+", -1);
        return new Commit(hashes[0], CommitMessages.parse(message), DiffStats.parse(numstat), hashes.length > 2);
    }
}
