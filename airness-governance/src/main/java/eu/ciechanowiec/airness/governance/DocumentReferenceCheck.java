package eu.ciechanowiec.airness.governance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Every repository file and npm script an {@code AGENTS.md} names still exists.
 *
 * <p>Every {@code AGENTS.md} in the committable tree is read, at the root and in each directory that
 * carries one, because an agent reads the nearest. A path is tried beside the document first and then at
 * the repository root: a nested document names files of its own directory, and also files the root holds.
 * What the union still catches is the one thing this is for, a name that resolves nowhere.
 *
 * <p>The npm scripts and dependencies are the node layer's input, read from the {@code package.json} beside
 * the document and at the root. A token that starts with a declared dependency is a module specifier, such as
 * {@code @airness/node/tsconfig.json}, rather than a repository path. A script name must be declared by one
 * of those two manifests, and where neither exists no script is judged, since no npm script could be meant.
 */
public final class DocumentReferenceCheck {

    private static final String INSTRUCTIONS = "AGENTS.md";
    private static final String STALE
        = "Agent instruction files that name a file which does not exist (fix or remove each reference)";
    private static final String UNDECLARED = "Agent instruction files that name an npm script no package.json "
        + "beside them or at the root declares (fix or remove each reference)";
    /*
     * The node gate ids a script family also matches. Documenting a gate is not naming a script.
     */
    private static final Set<String> GATES = Set.of("knip");

    private final Path root;
    private final List<Path> documents;
    private final Function<Path, Optional<PackageManifest>> manifests;

    /**
     * Names the working tree whose instruction files are read, and how a directory's {@code package.json} is
     * read.
     *
     * @param root      the working tree root
     * @param manifests the manifest in a directory, or empty where it has none
     */
    public DocumentReferenceCheck(Path root, Function<Path, Optional<PackageManifest>> manifests) {
        this.root = root;
        this.manifests = manifests;
        this.documents = Repository.trackedFiles(root).stream()
            .filter(file -> INSTRUCTIONS.equals(String.valueOf(file.getFileName())))
            .sorted()
            .toList();
    }

    /**
     * How many instruction files the check reads.
     *
     * @return the document count
     */
    public int scanned() {
        return this.documents.size();
    }

    /**
     * The references that resolve to nothing.
     *
     * @return one verdict for files and one for npm scripts
     */
    public List<Findings> findings() {
        return List.of(
            new Findings(STALE, this.documents.stream().flatMap(this::stale).toList()),
            new Findings(UNDECLARED, this.documents.stream().flatMap(this::undeclared).toList())
        );
    }

    private Stream<String> stale(Path document) {
        Path directory = this.directory(document);
        Set<String> packages = this.near(document).stream()
            .flatMap(manifest -> manifest.dependencies().stream())
            .collect(Collectors.toSet());
        return DocumentReferences.paths(Repository.readText(document).orElse("")).stream()
            .filter(path -> !packages.contains(DocumentReferences.specifierPackage(path)))
            .filter(path -> !Files.exists(directory.resolve(path)) && !Files.exists(this.root.resolve(path)))
            .map(path -> this.root.relativize(document) + ": " + path);
    }

    private Stream<String> undeclared(Path document) {
        List<PackageManifest> near = this.near(document);
        Set<String> scripts = near.stream().flatMap(manifest -> manifest.scripts().stream()).collect(
            Collectors.toSet()
        );
        return near.isEmpty() ? Stream.empty() : DocumentReferences.scripts(Repository.readText(document).orElse(""))
            .stream()
            .filter(script -> !scripts.contains(script) && !GATES.contains(script))
            .map(script -> this.root.relativize(document) + ": " + script);
    }

    private List<PackageManifest> near(Path document) {
        return Stream.of(this.directory(document), this.root)
            .distinct()
            .map(this.manifests)
            .flatMap(Optional::stream)
            .toList();
    }

    private Path directory(Path document) {
        return Optional.ofNullable(document.getParent()).orElse(this.root);
    }
}
