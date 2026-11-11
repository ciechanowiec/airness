package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;

/**
 * Finds the {@code .gitignore} negations that git never applies, because an exclusion hides a directory above
 * the path they name.
 *
 * <p>Git does not look inside an excluded directory, so no pattern can re-include a path beneath it: a
 * {@code .vscode/} line makes a later {@code !.vscode/settings.json} dead, while {@code .vscode/*} excludes the
 * contents and leaves the negation working. A dead negation reads as a decision to keep a file while recording
 * none, and a file it was meant to keep is silently ignored once it is first created.
 *
 * <p>The judgement is conservative, so a negation named here is one git never applies. Only the directories a
 * negation spells out are judged: those its anchored pattern writes, before its last segment, without a
 * wildcard. A directory is hidden when the last pattern that matches it, in the order git applies the
 * {@code .gitignore} files above it, is an exclusion. Where a pattern {@link GitGlob} does not model is reached
 * first, the directory is not judged.
 */
@UtilityClass
final class HiddenNegations {

    private static final String SEPARATOR = "/";

    /**
     * Every negation of the given files that an exclusion of a directory above its path hides.
     *
     * @param gitIgnores the {@code .gitignore} files of the working tree
     * @return each hidden negation once, in the order of the files and their lines
     */
    static List<HiddenNegation> in(List<IgnoreFile> gitIgnores) {
        return gitIgnores.stream()
            .flatMap(file -> file.patterns().stream())
            .filter(IgnorePattern::negated)
            .flatMap(negation -> hidden(negation, gitIgnores).stream())
            .toList();
    }

    /**
     * The directories above the path a negation names that its pattern spells out, shallowest first.
     *
     * @param negation a negation of a {@code .gitignore}
     * @return the directories, relative to the working tree root
     */
    static List<String> spelledDirectories(IgnorePattern negation) {
        String body = negation.text().substring(1);
        String path = body.endsWith(SEPARATOR) ? body.substring(0, body.length() - SEPARATOR.length()) : body;
        List<String> segments = List.of((path.startsWith(SEPARATOR) ? path.substring(1) : path).split(SEPARATOR));
        int literal = (int) segments.subList(0, segments.size() - 1).stream().takeWhile(GitGlob::literal).count();
        String base = negation.directory().isEmpty() ? "" : negation.directory() + SEPARATOR;
        return IntStream.rangeClosed(1, literal)
            .mapToObj(depth -> base + String.join(SEPARATOR, segments.subList(0, depth)))
            .toList();
    }

    private static Optional<HiddenNegation> hidden(IgnorePattern negation, List<IgnoreFile> gitIgnores) {
        return spelledDirectories(negation).stream()
            .flatMap(directory -> hiddenBy(negation, directory, gitIgnores).stream())
            .findFirst();
    }

    private static Optional<HiddenNegation> hiddenBy(
        IgnorePattern negation, String directory, List<IgnoreFile> gitIgnores
    ) {
        return exclusion(directory, gitIgnores).map(exclusion -> new HiddenNegation(negation, exclusion, directory));
    }

    // The exclusion that decides a directory, when the last pattern matching it is one.
    private static Optional<IgnorePattern> exclusion(String directory, List<IgnoreFile> gitIgnores) {
        List<IgnorePattern> deciding = IgnoreFile.above(directory, gitIgnores);
        // The last pattern that does not pass over the directory is either one this reader cannot model, which
        // leaves the directory undecided, or the one that decides it.
        return IntStream.iterate(deciding.size() - 1, index -> index >= 0, index -> index - 1)
            .mapToObj(deciding::get)
            .filter(pattern -> !passesOver(pattern, directory))
            .findFirst()
            .filter(pattern -> !pattern.negated() && GitIgnoreGlob.of(pattern).isPresent());
    }

    // Whether git reads the pattern and finds that it does not match the directory.
    private static boolean passesOver(IgnorePattern pattern, String directory) {
        return GitIgnoreGlob.of(pattern).map(glob -> !glob.matches(directory, true)).orElse(false);
    }
}
