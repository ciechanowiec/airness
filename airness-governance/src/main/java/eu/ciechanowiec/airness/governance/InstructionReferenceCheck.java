package eu.ciechanowiec.airness.governance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Every repository path and every type name that the root instruction file references resolves to
 * something the repository holds.
 *
 * <p>A type name the instructions mention must be a type the repository declares, a rule or inspection
 * the analysis configuration names, or a type the sources import. Those three are where every name the
 * instructions can honestly use comes from, so no allowlist is needed and a name that survives only in
 * the prose is reported.
 *
 * <p>An absent instruction file throws rather than being reported, which is the opposite of what
 * {@link EntryFileCheck} does with the same absence, and deliberately so. That check asserts the file is
 * there, so absence is its finding. This one has nothing to read without it, and a verdict of no
 * offences would render as a pass over a document that was never opened.
 */
public final class InstructionReferenceCheck {

    private static final String PATHS = "Broken repository references in %s";
    private static final String TYPES = "%s names types that this repository does not have";
    private static final String IMPORT = "import ";

    private final Path root;
    private final Path instructionFile;
    private final String configuration;
    private final Set<String> names;

    /**
     * Reads the instruction file, the analysis configuration, and the sources that supply resolvable
     * names.
     *
     * @param root               the working tree root
     * @param instructionFile    the repository-relative path of the root instruction file
     * @param configurationPaths files and directories whose text names rules and inspections
     * @param sourceRoots        the directories whose Java sources declare and import types
     */
    public InstructionReferenceCheck(
        Path root, Path instructionFile, Collection<Path> configurationPaths, Collection<Path> sourceRoots
    ) {
        this.root = root;
        this.instructionFile = instructionFile;
        this.configuration = configuration(root, configurationPaths);
        this.names = names(JavaSources.under(root, sourceRoots));
    }

    /**
     * Both reference rules, each reported separately so a failure names which one was broken.
     *
     * @return one verdict per rule
     */
    public List<Findings> findings() {
        String content = this.instructions();
        return List.of(
            new Findings(PATHS.formatted(this.instructionFile), this.unresolvedPaths(content)),
            new Findings(TYPES.formatted(this.instructionFile), this.unresolvedTypes(content))
        );
    }

    private List<String> unresolvedPaths(CharSequence content) {
        return InstructionReferenceRules.unresolved(
            content, Repository.topLevelDirectories(this.root), token -> Files.exists(this.root.resolve(token))
        );
    }

    private List<String> unresolvedTypes(CharSequence content) {
        return InstructionReferenceRules.unresolvedTypes(
            content, name -> this.names.contains(name) || this.configuration.contains(name)
        );
    }

    private String instructions() {
        return Repository.readText(this.root.resolve(this.instructionFile)).orElseThrow(
            () -> new IllegalStateException(
                "The instruction file is missing, so nothing could be checked: " + this.instructionFile
            )
        );
    }

    private static Set<String> names(Collection<Path> sources) {
        return Stream.concat(declared(sources), imported(sources)).collect(Collectors.toUnmodifiableSet());
    }

    private static Stream<String> declared(Collection<Path> sources) {
        return sources.stream().map(file -> file.getFileName().toString().replace(".java", ""));
    }

    private static Stream<String> imported(Collection<Path> sources) {
        return sources.stream()
            .map(Repository::readText)
            .flatMap(Optional::stream)
            .flatMap(String::lines)
            .filter(line -> line.startsWith(IMPORT))
            .map(InstructionReferenceCheck::simpleName);
    }

    private static String simpleName(String importLine) {
        String qualified = importLine.replace(IMPORT, "").replace("static ", "").replace(";", "").strip();
        return qualified.substring(qualified.lastIndexOf('.') + 1);
    }

    private static String configuration(Path root, Collection<Path> paths) {
        return Repository.trackedFiles(root).stream()
            .filter(file -> paths.stream().anyMatch(path -> file.startsWith(root.resolve(path))))
            .map(Repository::readText)
            .flatMap(Optional::stream)
            .collect(Collectors.joining(System.lineSeparator()));
    }
}
