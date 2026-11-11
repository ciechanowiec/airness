package eu.ciechanowiec.airness.governance;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One {@code .gitignore} pattern as git matches it against a concrete path of the working tree.
 *
 * <p>A pattern with a separator at its start or in its middle is anchored: git matches it against the path
 * relative to the directory of its own file. Any other pattern is matched against the last segment of a path,
 * so it applies at every depth beneath its file. A trailing separator restricts a pattern to directories.
 * {@link GitGlob} translates the glob, and a glob it does not model has no instance here.
 *
 * <p>Two patterns may share a path unless one of them is literal and the other provably misses what that
 * literal names. The answer is conservative by construction: where it can't be shown that no path matches
 * both, the patterns may share one.
 */
final class GitIgnoreGlob {

    private static final String SEPARATOR = "/";
    private static final String WHOLE_DEPTH = "**";

    private final String directory;
    private final Shape shape;
    private final Pattern regex;

    private GitIgnoreGlob(String directory, Shape shape, Pattern regex) {
        this.directory = directory;
        this.shape = shape;
        this.regex = regex;
    }

    /**
     * Reads one pattern of a {@code .gitignore}.
     *
     * @param pattern the pattern
     * @return how git matches it, or empty where its glob uses a form {@link GitGlob} does not model
     */
    static Optional<GitIgnoreGlob> of(IgnorePattern pattern) {
        String body = pattern.negated() ? pattern.text().substring(1) : pattern.text();
        boolean directoryOnly = body.length() > SEPARATOR.length() && body.endsWith(SEPARATOR);
        String path = directoryOnly ? body.substring(0, body.length() - SEPARATOR.length()) : body;
        boolean anchored = path.contains(SEPARATOR);
        String glob = path.startsWith(SEPARATOR) ? path.substring(SEPARATOR.length()) : path;
        Shape shape = new Shape(glob, anchored, directoryOnly);
        return GitGlob.regex(glob).map(regex -> new GitIgnoreGlob(pattern.directory(), shape, regex));
    }

    /**
     * Whether some path of the working tree may be matched by both patterns.
     *
     * @param first  one pattern of a {@code .gitignore}
     * @param second another pattern of a {@code .gitignore}
     * @return false only where no path can be matched by both
     */
    static boolean mayShareAPath(IgnorePattern first, IgnorePattern second) {
        Optional<GitIgnoreGlob> one = of(first);
        Optional<GitIgnoreGlob> other = of(second);
        return one.isEmpty() || other.isEmpty() || one.get().sharesAPathWith(other.get());
    }

    /**
     * Whether git matches the pattern against a path.
     *
     * @param path        the path, relative to the working tree root and separated by forward slashes
     * @param isDirectory whether the path names a directory
     * @return whether the pattern matches the path
     */
    boolean matches(String path, boolean isDirectory) {
        boolean kindMatches = isDirectory || !this.shape.directoryOnly();
        return kindMatches && this.relative(path)
            .map(relative -> this.regex.matcher(this.shape.anchored() ? relative : lastSegment(relative)).matches())
            .orElse(false);
    }

    private boolean sharesAPathWith(GitIgnoreGlob other) {
        if (GitGlob.literal(other.shape.glob())) {
            return this.mayMatchWhatNames(other);
        }
        return !GitGlob.literal(this.shape.glob()) || other.mayMatchWhatNames(this);
    }

    // A literal anchored pattern names one path, and a literal un-anchored one names every path ending in it.
    private boolean mayMatchWhatNames(GitIgnoreGlob literal) {
        String literalGlob = literal.shape.glob();
        if (literal.shape.anchored()) {
            String named = literal.directory.isEmpty() ? literalGlob : literal.directory + SEPARATOR + literalGlob;
            return this.matches(named, true);
        }
        return this.mayEndIn(literalGlob);
    }

    // Whether a path this pattern matches may end in the given segment.
    private boolean mayEndIn(String segment) {
        if (!this.shape.anchored()) {
            return this.regex.matcher(segment).matches();
        }
        String last = lastSegment(this.shape.glob());
        return WHOLE_DEPTH.equals(last) || GitGlob.regex(last)
            .map(lastRegex -> lastRegex.matcher(segment).matches())
            .orElse(true);
    }

    private Optional<String> relative(String path) {
        if (this.directory.isEmpty()) {
            return Optional.of(path);
        }
        String prefix = this.directory + SEPARATOR;
        return path.startsWith(prefix) ? Optional.of(path.substring(prefix.length())) : Optional.empty();
    }

    private static String lastSegment(String path) {
        return path.substring(path.lastIndexOf(SEPARATOR) + 1);
    }

    /**
     * What the line of a pattern says besides its glob.
     *
     * @param glob          the glob, without the negation, the leading separator or the trailing separator
     * @param anchored      whether git matches the glob against the path relative to the file's directory
     * @param directoryOnly whether the pattern matches directories alone
     */
    private record Shape(String glob, boolean anchored, boolean directoryOnly) {
    }
}
