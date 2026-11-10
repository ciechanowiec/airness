package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The repository carries its instructions in {@code AGENTS.md}, and in no other agent instruction file.
 *
 * <p>The managed blocks at the start of {@code AGENTS.md} are checked with the other managed files, by
 * {@link AssetCheck}, because which blocks a file needs depends on the layers of the repository.
 *
 * <p>Claude Code reads {@code AGENTS.md} itself, so a {@code CLAUDE.md} could only add a second source of
 * instructions beside it. Such files are refused in every directory, since Claude Code also loads nested
 * ones. The names are constants rather than project parameters, and no setting exempts a path, because
 * a configurable rule could pass over the very file the tool would read.
 *
 * <p>The rule directories of the other agent tools and a {@code GEMINI.md} below the root are refused the
 * same way, because each holds instructions of its own beside {@code AGENTS.md}.
 */
public final class EntryFileCheck {

    private static final String MISSING_INSTRUCTIONS = "The mandatory AGENTS.md file is missing";
    private static final String EMPTY_INSTRUCTIONS = "AGENTS.md contains no instructions";
    private static final String FORBIDDEN_CLAUDE
        = "CLAUDE.md files are forbidden because Claude Code reads AGENTS.md (delete them)";
    private static final String FORBIDDEN_TOOL_RULES = "Agent tool rule directories and nested GEMINI.md files are "
        + "forbidden because they hold instructions beside AGENTS.md (delete them, and point the tool at AGENTS.md "
        + "from its root reference file)";

    private final Path root;

    /**
     * Names the working tree that must carry {@code AGENTS.md} and no {@code CLAUDE.md}.
     *
     * @param root the working tree root
     */
    public EntryFileCheck(Path root) {
        this.root = root;
    }

    /**
     * The ways this arrangement breaks, each reported separately.
     *
     * @return one verdict per rule
     */
    public List<Findings> findings() {
        return List.of(
            new Findings(MISSING_INSTRUCTIONS, this.missingInstructions()),
            new Findings(EMPTY_INSTRUCTIONS, this.emptyInstructions()),
            new Findings(FORBIDDEN_CLAUDE, this.forbiddenClaude()),
            new Findings(FORBIDDEN_TOOL_RULES, this.forbiddenToolRules())
        );
    }

    private List<String> missingInstructions() {
        return this.readInstructions().isEmpty()
            ? List.of(EntryFileRules.INSTRUCTIONS)
            : List.of();
    }

    private List<String> emptyInstructions() {
        return this.readInstructions()
            .filter(content -> !EntryFileRules.hasInstructions(content))
            .map(_ -> List.of(EntryFileRules.INSTRUCTIONS))
            .orElseGet(List::of);
    }

    private List<String> forbiddenClaude() {
        return Repository.trackedFiles(this.root).stream()
            .filter(EntryFileRules::isForbidden)
            .map(file -> this.root.relativize(file).toString())
            .toList();
    }

    private List<String> forbiddenToolRules() {
        return Repository.trackedFiles(this.root).stream()
            .map(this.root::relativize)
            .filter(EntryFileRules::isToolInstruction)
            .map(Path::toString)
            .toList();
    }

    private Optional<String> readInstructions() {
        return Repository.readText(this.root.resolve(EntryFileRules.INSTRUCTIONS));
    }
}
