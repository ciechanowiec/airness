package eu.ciechanowiec.airness.governance;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
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
 */
public final class EditorconfigExcludes {

    private static final String HEADER = """
        # Written by airness:editorconfig-excludes, and read by the file linter as patterns to skip.
        # Every line below is a path git is configured never to carry, asked of git rather than
        # translated out of .gitignore, because a linter reads these as globs and gitignore is not one.
        """;

    private static final String SEPARATOR = "/";

    private static final String EVERYTHING_BELOW = "/**";

    // What a glob reads as syntax rather than as part of a name. A path is a name, so each of them is
    // escaped: without this a directory called logs[old] would be read as a character class and would
    // exclude something else, or nothing.
    private static final String SYNTAX = "*?[]{}\\";

    private final Path root;

    private final Path module;

    /**
     * Reads the ignored paths of one module out of the working tree that holds it.
     *
     * @param root   the working tree root, which is where git is asked
     * @param module the base directory of the module whose linter is being configured
     */
    public EditorconfigExcludes(Path root, Path module) {
        this.root = root;
        this.module = module;
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
     * The ignored paths of this module, as globs relative to the module rather than to the working
     * tree, because that is what a linter matches them against.
     *
     * @return one or two globs per ignored path, sorted, without repetition
     */
    public List<String> patterns() {
        String prefix = this.modulePrefix();
        return this.ignored().stream()
            .filter(entry -> entry.startsWith(prefix))
            .map(entry -> entry.substring(prefix.length()))
            .filter(entry -> !entry.isEmpty())
            .flatMap(EditorconfigExcludes::globs)
            .distinct()
            .sorted()
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
        String escaped = escaped(named);
        return entry.endsWith(SEPARATOR) ? Stream.of(escaped, escaped + EVERYTHING_BELOW) : Stream.of(escaped);
    }

    private static String escaped(String name) {
        StringBuilder written = new StringBuilder(name.length());
        name.chars().forEach(character -> escape(written, (char) character));
        return written.toString();
    }

    private static void escape(StringBuilder written, char character) {
        if (SYNTAX.indexOf(character) >= 0) {
            written.append('\\');
        }
        written.append(character);
    }
}
