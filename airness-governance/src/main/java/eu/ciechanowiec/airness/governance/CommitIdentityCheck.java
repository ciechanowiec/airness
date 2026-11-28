package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Every email the history records, as an author or as a committer, stands for exactly one name, so one
 * person never appears in the history as two.
 *
 * <p>Names are compared exactly as each commit records them. A {@code .mailmap} is not read, because it
 * changes only how some tools display a commit and leaves the recorded identity, which every other tool and
 * every later reader sees, as it was. One email under {@code Doe} and under {@code Doe, Jane} is two names,
 * and only a rewrite of the history makes it one.
 *
 * <p>This walks the whole history, so it belongs to the slow verification profile, and it is worth nothing
 * over a shallow clone: the identities that were never fetched pass by not existing. The caller refuses a
 * shallow repository before running this, which is why {@link Repository} can say whether one is shallow.
 */
public final class CommitIdentityCheck {

    private static final String HEADLINE = "Emails recorded under more than one name";
    private static final String SEPARATOR = Character.toString(0);
    /*
     * Each field is closed by a NUL byte, which no commit header can hold, so a name may carry any other text.
     * The placeholders without a capital letter are the identities as recorded, never as a mailmap maps them,
     * and the mailmap is turned off besides. The oldest commit comes first, so the first name an email was
     * recorded under is named first.
     */
    private static final List<String> COMMAND = List.of(
        "log", "--no-mailmap", "--reverse", "--format=%H%x00%an%x00%ae%x00%cn%x00%ce%x00", "HEAD"
    );
    private static final int FIELDS_PER_COMMIT = 5;

    private final List<Identity> identities;
    private final int commits;

    /**
     * Reads the whole history.
     *
     * @param root the working tree root
     */
    public CommitIdentityCheck(Path root) {
        List<String> fields = Arrays.asList(GitPlumbing.run(root, COMMAND).split(SEPARATOR, -1));
        this.commits = fields.size() / FIELDS_PER_COMMIT;
        this.identities = IntStream.range(0, this.commits)
            .mapToObj(index -> fields.subList(index * FIELDS_PER_COMMIT, (index + 1) * FIELDS_PER_COMMIT))
            .flatMap(CommitIdentityCheck::identities)
            .toList();
    }

    /**
     * How many commits the check read, which a caller logs so the reach of a clean verdict is on the
     * record.
     *
     * @return the number of commits in scope
     */
    public int scanned() {
        return this.commits;
    }

    /**
     * Every email recorded under more than one name, each naming the names and the first commit that
     * recorded each of them.
     *
     * @return the single verdict this check produces
     */
    public List<Findings> findings() {
        Map<String, SequencedMap<String, Identity>> names = this.identities.stream()
            .collect(
                Collectors.groupingBy(
                    Identity::email, LinkedHashMap::new,
                    Collectors.toMap(Identity::name, identity -> identity, (first, _) -> first, LinkedHashMap::new)
                )
            );
        return List.of(
            new Findings(
                HEADLINE,
                names.entrySet().stream()
                    .filter(email -> email.getValue().size() > 1)
                    .map(email -> offence(email.getKey(), email.getValue()))
                    .toList()
            )
        );
    }

    private static Stream<Identity> identities(List<String> fields) {
        String sha = fields.getFirst().strip();
        return Stream.of(
            new Identity(sha, "author", fields.get(1), fields.get(2)),
            new Identity(sha, "committer", fields.get(3), fields.get(4))
        );
    }

    private static String offence(String email, SequencedMap<String, Identity> names) {
        String recorded = names.values().stream()
            .map(identity -> "'%s' (%s of %s)".formatted(identity.name(), identity.role(), identity.sha()))
            .collect(Collectors.joining(", "));
        return "<%s> is recorded under %d names, where one person keeps one: %s"
            .formatted(email, names.size(), recorded);
    }

    /**
     * One identity a commit records, in the role it records it.
     */
    private record Identity(String sha, String role, String name, String email) {
    }
}
