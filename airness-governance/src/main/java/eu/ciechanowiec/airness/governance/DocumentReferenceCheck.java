package eu.ciechanowiec.airness.governance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Every repository file an {@code AGENTS.md} names still exists.
 *
 * <p>Every {@code AGENTS.md} in the committable tree is read, at the root and in each directory that
 * carries one, because an agent reads the nearest. A path is tried beside the document first and then at
 * the repository root: a nested document names files of its own directory, and also files the root holds.
 * What the union still catches is the one thing this is for, a name that resolves nowhere.
 *
 * <p>The npm scripts a document names are an input of the node layer. The generic layer reads paths only.
 */
public final class DocumentReferenceCheck {

    private static final String INSTRUCTIONS = "AGENTS.md";
    private static final String STALE
        = "Agent instruction files that name a file which does not exist (fix or remove each reference)";

    private final Path root;
    private final List<Path> documents;

    /**
     * Names the working tree whose instruction files are read.
     *
     * @param root the working tree root
     */
    public DocumentReferenceCheck(Path root) {
        this.root = root;
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
     * The references that resolve to no file.
     *
     * @return one verdict
     */
    public List<Findings> findings() {
        return List.of(new Findings(STALE, this.documents.stream().flatMap(this::stale).toList()));
    }

    private Stream<String> stale(Path document) {
        Path directory = Optional.ofNullable(document.getParent()).orElse(this.root);
        return DocumentReferences.paths(Repository.readText(document).orElse("")).stream()
            .filter(path -> !Files.exists(directory.resolve(path)) && !Files.exists(this.root.resolve(path)))
            .map(path -> this.root.relativize(document) + ": " + path);
    }
}
