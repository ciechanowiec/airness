package eu.ciechanowiec.airness.governance;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * The paths of one module that git is configured never to carry, written as the globs a file linter
 * excludes by.
 *
 * <p>A file git ignores is build output or a tool's scratch, and neither is content this repository's
 * style governs. The linter that reads the tree has no notion of git, so it reads them anyway and
 * reports a missing newline in a directory nobody wrote. Handing it this list is what makes the set it
 * reads the set a commit would carry, which is already how {@link Repository#trackedFiles} answers the
 * same question for every other check.
 *
 * <p>The list is asked of git rather than translated from {@code .gitignore}, because the two
 * languages only look alike. A linter compiles each line as a glob, where {@code target/} matches
 * nothing, a leading {@code *.log} matches one segment rather than every directory, and a {@code !}
 * negation is a literal name. A translation that got any of those wrong would exclude nothing and read
 * as though it had.
 *
 * <p>Whole ignored directories are asked for as directories, so a tree carrying a build output
 * directory or a dependency cache contributes two lines rather than one per file inside it.
 *
 * <p>The files a generator owns are excluded too, as the typography and source-rule checks leave them: a
 * generator writes them as it writes them, without the final newline the style asks for, and the agent
 * runtime may not edit them, so a finding there could not be repaired where it is reported. They arrive as
 * repository-relative globs, which are matched here, as typography matches them, and passed on as the paths
 * they match. Passing the globs on would not do: the linter reads Ant patterns, which have no brace
 * alternatives, and a glob that opens with a wildcard names no module and so reaches none of them.
 *
 * <p>The linter's patterns know {@code *}, {@code ?} and {@code **} and nothing else, and they read a
 * backslash as a path separator rather than as an escape. A path is therefore written as it is, save for a
 * {@code *} or {@code ?} in a name, which only {@code ?} can stand for.
 */
public final class EditorconfigExcludes {

    private static final String HEADER = """
        # Written by airness:editorconfig-excludes, and read by the file linter as patterns to skip.
        # Every line below is a path git is configured never to carry, asked of git rather than
        # translated out of .gitignore, because a linter reads these as globs and gitignore is not one,
        # or a file a generator owns, matched from the globs the project declares generated.
        """;

    private static final String SEPARATOR = "/";

    private static final String EVERYTHING_BELOW = "/**";

    // What the linter's patterns read as a wildcard. Nothing escapes one, so a name holding either is
    // written with the one-character wildcard in its place, which matches the name and its near twins.
    private static final String WILDCARDS = "*?";

    private static final char ANY_CHARACTER = '?';

    private final Path root;

    private final Path module;

    private final List<String> generated;

    /**
     * Reads the ignored paths of one module out of the working tree that holds it.
     *
     * @param root   the working tree root, which is where git is asked
     * @param module the base directory of the module whose linter is being configured
     */
    public EditorconfigExcludes(Path root, Path module) {
        this(root, module, List.of());
    }

    /**
     * Reads the ignored paths of one module, and takes the files a generator owns.
     *
     * @param root      the working tree root, which is where git is asked
     * @param module    the base directory of the module whose linter is being configured
     * @param generated repository-relative globs of the files a generator owns
     */
    public EditorconfigExcludes(Path root, Path module, Collection<String> generated) {
        this.root = root;
        this.module = module;
        this.generated = List.copyOf(generated);
    }

    /**
     * The whole file a linter reads, header and patterns.
     *
     * @return the text to write, which is the header alone when the module has nothing ignored under it
     */
    public String document() {
        return HEADER + String.join(System.lineSeparator(), this.patterns()) + System.lineSeparator();
    }

    /**
     * The ignored paths and the generated files of this module, as patterns relative to the module
     * rather than to the working tree, because that is what a linter matches them against.
     *
     * @return one or two patterns per ignored path and one per generated file, sorted, without repetition
     */
    public List<String> patterns() {
        String prefix = this.modulePrefix();
        Stream<String> ignored = this.ignored().stream()
            .filter(entry -> entry.startsWith(prefix))
            .map(entry -> entry.substring(prefix.length()))
            .filter(entry -> !entry.isEmpty())
            .flatMap(EditorconfigExcludes::globs);
        Stream<String> owned = this.owned().stream()
            .map(file -> this.module.relativize(file).toString().replace(File.separatorChar, '/'))
            .map(EditorconfigExcludes::literal);
        return Stream.concat(ignored, owned).distinct().sorted().toList();
    }

    // The committable files below the module that a generated glob matches, each glob read against the
    // path relative to the working tree root, which is what it is written relative to. Git is not asked
    // at all when nothing is declared generated, which is the ordinary module.
    private List<Path> owned() {
        if (this.generated.isEmpty()) {
            return List.of();
        }
        List<PathMatcher> matchers = this.generated.stream()
            .map(glob -> FileSystems.getDefault().getPathMatcher("glob:" + glob))
            .toList();
        return Repository.committableFiles(this.module).orElseGet(List::of).stream()
            .filter(file -> matchers.stream().anyMatch(matcher -> matcher.matches(this.root.relativize(file))))
            .toList();
    }

    // Every ignored entry, as git spells them: relative to the working tree root, separated by a
    // forward slash whatever the platform, and with a trailing slash on a directory that is ignored
    // whole.
    private List<String> ignored() {
        String listing = GitPlumbing.run(
            this.root, List.of("ls-files", "-z", "--others", "--ignored", "--exclude-standard", "--directory")
        );
        return Arrays.stream(listing.split("\0", -1)).filter(entry -> !entry.isEmpty()).toList();
    }

    // Where this module sits under the working tree, spelled the way git spells a path, so that an
    // entry can be tested against it as text. The root module sits at the top and carries no prefix,
    // which is what lets every ignored path in the tree belong to it.
    private String modulePrefix() {
        String relative = this.root.relativize(this.module).toString().replace(File.separatorChar, '/');
        return relative.isEmpty() ? "" : relative + SEPARATOR;
    }

    // A directory has to be named twice: once as itself, so the linter passes over the entry, and once
    // as everything below it, because a glob that names a directory says nothing about its contents.
    private static Stream<String> globs(String entry) {
        String named = entry.endsWith(SEPARATOR) ? entry.substring(0, entry.length() - 1) : entry;
        String literal = literal(named);
        return entry.endsWith(SEPARATOR) ? Stream.of(literal, literal + EVERYTHING_BELOW) : Stream.of(literal);
    }

    private static String literal(String name) {
        StringBuilder written = new StringBuilder(name.length());
        name.chars().forEach(
            character -> written.append(WILDCARDS.indexOf(character) >= 0 ? ANY_CHARACTER : (char) character)
        );
        return written.toString();
    }
}
