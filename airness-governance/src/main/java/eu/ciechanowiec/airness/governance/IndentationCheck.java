package eu.ciechanowiec.airness.governance;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Every tracked text file indents the way the {@code .editorconfig} it answers to declares.
 *
 * <p>The file linter of the same build holds a file to the line ending, the final newline and the trailing
 * whitespace its {@code .editorconfig} declares, and to its indentation only where the file is XML. This check
 * holds every file to {@code indent_style}, and a YAML file to {@code indent_size} as well, because YAML reads
 * its structure out of indentation. {@code indent_size} is no rule elsewhere: a continuation aligned under the
 * line above it, a list item's second line, and a diagram all indent by columns that are not nesting steps.
 *
 * <p>The files read are the committable ones, apart from those a generator owns and those a file role
 * exempts. Each is reported at its first departure only, naming the line and the property it breaks, since
 * a file indented with the wrong whitespace throughout would otherwise fill the report with one cause.
 */
public final class IndentationCheck {

    private static final String DEVIATIONS
        = "Indentation that departs from the .editorconfig the file answers to (indent with the whitespace "
            + "indent_style names, and nest YAML by indent_size)";
    private static final List<String> YAML = List.of(".yml", ".yaml");
    private static final char SEPARATOR = '/';

    private final Path root;
    private final AuthoredFiles files;
    private final List<Path> selected;
    private final EditorconfigLookup lookup;

    /**
     * Selects the files to read, leaving out those a generator owns and those a file role exempts.
     *
     * @param root      the working tree root
     * @param generated repository-relative globs of the files a generator owns
     * @param exempt    whether a file role exempts a path, asked of the path from the working tree root written
     *                  with forward slashes
     */
    public IndentationCheck(Path root, Collection<String> generated, Predicate<String> exempt) {
        this.root = root;
        this.files = AuthoredFiles.of(root, generated);
        this.selected = this.files.authored().stream()
            .filter(file -> !exempt.test(relative(root, file)))
            .toList();
        this.lookup = new EditorconfigLookup(root);
    }

    /**
     * How many committable files a generator owns, which the check left unread.
     *
     * @return the count
     */
    public long generated() {
        return this.files.generated();
    }

    /**
     * How many files the check reads, which a caller refuses when it is zero.
     *
     * @return the number of committable files in scope
     */
    public int scanned() {
        return this.selected.size();
    }

    /**
     * The first departure of every file that departs.
     *
     * @return one verdict
     */
    public List<Findings> findings() {
        List<String> deviations = this.selected.stream()
            .flatMap(file -> this.deviation(file).stream())
            .toList();
        return List.of(new Findings(DEVIATIONS, deviations));
    }

    private Optional<String> deviation(Path file) {
        return Repository.readText(file)
            .flatMap(text -> this.first(file, text.lines().toList()))
            .map(found -> relative(this.root, file) + ": " + found.explanation());
    }

    private Optional<IndentationDeviation> first(Path file, List<String> lines) {
        IndentationSettings settings = this.lookup.settings(file);
        Optional<IndentationDeviation> style = settings.style()
            .flatMap(declared -> IndentStyleRule.first(lines, declared));
        Optional<IndentationDeviation> nesting = yaml(file)
            ? settings.size().flatMap(size -> YamlNesting.first(lines, size))
            : Optional.empty();
        return Stream.of(style, nesting)
            .flatMap(Optional::stream)
            .min(Comparator.comparingInt(IndentationDeviation::line));
    }

    private static boolean yaml(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return YAML.stream().anyMatch(name::endsWith);
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace(File.separatorChar, SEPARATOR);
    }
}
