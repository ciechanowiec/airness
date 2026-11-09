package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * No ignore file repeats a pattern that an earlier declaration already applies.
 *
 * <p>A repeat changes nothing git or Docker ignores, so it reads as a decision while recording none, and it
 * outlives the declaration it copies: once that declaration changes, the copy keeps the old rule alive in a place
 * nobody looks. The repair is always to remove the repeat. A repeat is judged conservatively, so a line this check
 * names is one whose removal changes nothing:
 *
 * <ul>
 * <li>In one file, a line that holds the same pattern as an earlier line, with no line of the other polarity
 * between them. A negation between two exclusions, or an exclusion between two negations, can change which
 * of them decides a path, so the later one is then not a repeat.</li>
 * <li>In a {@code .gitignore}, also an un-anchored line, one with no separator but an optional trailing one, that
 * holds the same pattern as a line of a {@code .gitignore} in a directory above, with no line of the other
 * polarity between them in the order git applies the files: the directory above first, then each one
 * beneath it. Git applies an un-anchored pattern at every depth beneath its file, so the one above already
 * reaches every path the repeat reaches. An anchored pattern is relative to its own file's directory, so the
 * same text in two files names two different paths and is never a repeat.</li>
 * </ul>
 *
 * <p>A {@code .dockerignore}, or a {@code <name>.dockerignore} that belongs to one Dockerfile, is relative to the
 * build context whatever directory holds it, so only its own lines are compared.
 *
 * <p>A line in an Airness managed block counts as declared, so a line the project wrote that repeats it is named.
 * A managed line itself is never named, because the project cannot remove it. The shipped blocks are held to
 * repeating nothing where they are built.
 */
public final class IgnorePatternCheck {

    private static final String HEADLINE
        = "Ignore patterns that repeat a declaration git or Docker already applies (remove the repeat)";
    private static final Pattern GIT_IGNORE = Pattern.compile("^\\.gitignore$");
    private static final Pattern DOCKER_IGNORE = Pattern.compile("^.*\\.dockerignore$");

    private final int scanned;
    private final List<String> offences;

    /**
     * Reads every ignore file of the working tree once.
     *
     * @param root the working tree root
     */
    public IgnorePatternCheck(Path root) {
        List<Path> tracked = Repository.trackedFiles(root);
        List<IgnoreFile> gitIgnores = ignoreFiles(root, tracked, GIT_IGNORE);
        List<IgnoreFile> dockerIgnores = ignoreFiles(root, tracked, DOCKER_IGNORE);
        this.scanned = gitIgnores.size() + dockerIgnores.size();
        this.offences = repeats(gitIgnores, dockerIgnores).stream()
            .filter(repeat -> !repeat.repeat().managed())
            .map(IgnoreRepeat::offence)
            .toList();
    }

    /**
     * How many ignore files the check read, which a caller logs so the reach of a clean verdict is on the record.
     *
     * @return the number of files read
     */
    public int scanned() {
        return this.scanned;
    }

    /**
     * Every line the project wrote that repeats a declaration already applied.
     *
     * @return the single verdict this check produces
     */
    public List<Findings> findings() {
        return List.of(new Findings(HEADLINE, this.offences));
    }

    /**
     * Every repeated line of the given files, managed lines included.
     *
     * @param gitIgnores    the {@code .gitignore} files, compared with the ones in the directories above them
     * @param dockerIgnores the Docker ignore files, each compared with itself alone
     * @return every repeat, in the order of the files and their lines
     */
    static List<IgnoreRepeat> repeats(List<IgnoreFile> gitIgnores, List<IgnoreFile> dockerIgnores) {
        return Stream.concat(
            gitIgnores.stream().flatMap(file -> repeatsIn(file, above(file, gitIgnores))),
            dockerIgnores.stream().flatMap(file -> repeatsIn(file, List.of()))
        ).toList();
    }

    private static List<IgnoreFile> ignoreFiles(Path root, List<Path> tracked, Pattern name) {
        return tracked.stream()
            .filter(file -> name.matcher(String.valueOf(file.getFileName())).matches())
            .map(file -> IgnoreFile.read(root, file))
            .toList();
    }

    // The patterns of the files above, in the order git applies them: the root first, then each deeper directory.
    private static List<IgnorePattern> above(IgnoreFile file, List<IgnoreFile> gitIgnores) {
        return gitIgnores.stream()
            .filter(candidate -> candidate.above(file))
            .sorted(Comparator.comparingInt(candidate -> candidate.directory().length()))
            .flatMap(candidate -> candidate.patterns().stream())
            .toList();
    }

    private static Stream<IgnoreRepeat> repeatsIn(IgnoreFile file, List<IgnorePattern> inherited) {
        List<IgnorePattern> applied = Stream.concat(inherited.stream(), file.patterns().stream()).toList();
        return IntStream.range(inherited.size(), applied.size())
            .mapToObj(position -> repeat(applied, position, inherited.size()))
            .flatMap(Optional::stream);
    }

    // The nearest earlier declaration of the same pattern, when the line at the position repeats it.
    private static Optional<IgnoreRepeat> repeat(List<IgnorePattern> applied, int position, int inherited) {
        IgnorePattern line = applied.get(position);
        return IntStream.iterate(position - 1, earlier -> earlier >= 0, earlier -> earlier - 1)
            .filter(earlier -> applied.get(earlier).text().equals(line.text()))
            .boxed()
            .findFirst()
            .filter(earlier -> earlier >= inherited || line.unanchored())
            .filter(earlier -> unopposed(applied.subList(earlier + 1, position), line))
            .map(earlier -> new IgnoreRepeat(line, applied.get(earlier), earlier < inherited));
    }

    private static boolean unopposed(List<IgnorePattern> between, IgnorePattern line) {
        return between.stream().allMatch(other -> other.negated() == line.negated());
    }
}
