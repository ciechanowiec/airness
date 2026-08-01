package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Every entry file a repository ships holds a reference to the root instruction file and nothing else.
 *
 * <p>An agent tool reads a file under a name of its own, and splitting the rules across those files is
 * how they drift. The tool that reads the stale copy is then the one nobody notices, so there is one
 * copy and the rest point at it.
 *
 * <p>The declared names are the rule rather than a convenience: a tool a repository is opened to gets a
 * name here, and a file no name covers is not an entry file. An absent one is therefore reported rather
 * than skipped, because a list that silently tolerates a missing member states nothing at all.
 */
public final class EntryFileCheck {

    private static final String BEYOND = "An entry file states what only the instruction file may state";
    private static final String SILENT = "An entry file does not point at the instruction file";
    private static final String ABSENT = "A declared entry file is missing";
    private static final String NO_INSTRUCTIONS = "The instruction file every entry file points at is missing";

    private final Path root;
    private final String instructionFile;
    private final List<String> entryFiles;

    /**
     * Names the instruction file and the entry files that must point at it.
     *
     * @param root            the working tree root
     * @param instructionFile the repository-relative name of the root instruction file
     * @param entryFiles      the repository-relative names of the entry files shipped
     */
    public EntryFileCheck(Path root, String instructionFile, Collection<String> entryFiles) {
        this.root = root;
        this.instructionFile = instructionFile;
        this.entryFiles = List.copyOf(entryFiles);
    }

    /**
     * The four ways this arrangement breaks, each reported separately.
     *
     * @return one verdict per rule
     */
    public List<Findings> findings() {
        return List.of(
            new Findings(BEYOND, this.beyondTheReference()),
            new Findings(SILENT, this.silent()),
            new Findings(ABSENT, this.absent()),
            new Findings(NO_INSTRUCTIONS, this.missingInstructions())
        );
    }

    private List<String> beyondTheReference() {
        return this.entryFiles.stream()
            .flatMap(
                name -> this.read(name).stream()
                    .flatMap(content -> EntryFileRules.beyondTheReference(content, this.instructionFile).stream())
                    .map(line -> name + ": " + line)
            )
            .toList();
    }

    private List<String> silent() {
        return this.entryFiles.stream()
            .filter(name -> this.read(name).filter(content -> !this.references(content)).isPresent())
            .toList();
    }

    private boolean references(String content) {
        return EntryFileRules.referencesInstructionFile(content, this.instructionFile);
    }

    private List<String> absent() {
        return this.entryFiles.stream().filter(name -> this.read(name).isEmpty()).toList();
    }

    private List<String> missingInstructions() {
        return this.read(this.instructionFile).isPresent() ? List.of() : List.of(this.instructionFile);
    }

    private Optional<String> read(String name) {
        return Repository.readText(this.root.resolve(name)).filter(text -> !text.isBlank());
    }
}
