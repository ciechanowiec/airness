package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * One ignore file of the working tree, read line by line as git and Docker read it: a blank line and a line that
 * opens with {@code #} hold no pattern, and every other line holds one.
 *
 * @param path      the file, relative to the working tree root and separated by forward slashes
 * @param directory the directory the file stands in, relative to the root and empty for the root itself
 * @param patterns  every pattern of the file, in the order of its lines
 */
record IgnoreFile(String path, String directory, List<IgnorePattern> patterns) {

    private static final String COMMENT = "#";
    private static final String SEPARATOR = "/";
    // The pattern, then the spaces a tool drops: a backslash pairs with the character after it, so an escaped
    // space stays in the pattern, and a lone trailing backslash stays as written.
    private static final Pattern TRAILING_SPACES = Pattern.compile("(?s)^((?:\\\\.|[^\\\\])*?\\\\?) *\\z");

    /**
     * Makes a defensive copy so a reader cannot alter the patterns a verdict was reached on.
     *
     * @param path      the file, relative to the working tree root
     * @param directory the directory the file stands in, relative to the root
     * @param patterns  every pattern of the file
     */
    IgnoreFile {
        patterns = List.copyOf(patterns);
    }

    /**
     * Reads one ignore file of the working tree.
     *
     * @param root the working tree root
     * @param file the ignore file
     * @return the file and its patterns
     */
    static IgnoreFile read(Path root, Path file) {
        String path = root.relativize(file).toString().replace('\\', '/');
        return parse(path, Repository.readText(file).orElse(""));
    }

    /**
     * Reads the patterns of an ignore file's content.
     *
     * @param path    the file, relative to the working tree root and separated by forward slashes
     * @param content the content of the file
     * @return the file and its patterns
     */
    static IgnoreFile parse(String path, String content) {
        List<String> lines = List.of(content.split("\n", -1));
        int slash = path.lastIndexOf(SEPARATOR);
        String directory = slash < 0 ? "" : path.substring(0, slash);
        List<IgnorePattern> patterns = IntStream.range(0, lines.size())
            .mapToObj(index -> new IgnorePattern(path, index + 1, pattern(lines.get(index)), block(lines, index)))
            .filter(pattern -> !pattern.text().isEmpty() && !pattern.text().startsWith(COMMENT))
            .toList();
        return new IgnoreFile(path, directory, patterns);
    }

    /**
     * Whether this file stands in a directory above another one, where git applies its patterns too.
     *
     * @param other another ignore file
     * @return whether this file's directory holds the other's
     */
    boolean above(IgnoreFile other) {
        return !this.directory.equals(other.directory)
            && (this.directory.isEmpty() || other.directory.startsWith(this.directory + SEPARATOR));
    }

    private static String pattern(String line) {
        return TRAILING_SPACES.matcher(line).replaceFirst("$1");
    }

    // The layer of the nearest marker above the line when that marker opens a block. A line below a closing
    // marker, or below none, is the project's.
    private static String block(List<String> lines, int index) {
        return IntStream.iterate(index - 1, earlier -> earlier >= 0, earlier -> earlier - 1)
            .mapToObj(lines::get)
            .filter(ManagedSections::marks)
            .findFirst()
            .flatMap(ManagedSections::opened)
            .orElse("");
    }
}
