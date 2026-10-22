package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The repository carries the managed Airness section and project instructions in {@code AGENTS.md},
 * and in no other agent instruction file.
 *
 * <p>Claude Code reads {@code AGENTS.md} itself, so a {@code CLAUDE.md} could only add a second source of
 * instructions beside it. Such files are refused in every directory, since Claude Code also loads nested
 * ones. The names are constants rather than project parameters, and no setting exempts a path, because
 * a configurable rule could pass over the very file the tool would read.
 */
public final class EntryFileCheck {

    private static final String MISSING_INSTRUCTIONS = "The mandatory AGENTS.md file is missing";
    private static final String EMPTY_INSTRUCTIONS = "AGENTS.md contains no instructions";
    // An absent file leaves the Optional this rule reads empty, so the absence is MISSING_INSTRUCTIONS
    // above and never this. Naming it here as well would promise a condition this rule cannot report.
    private static final String MISSING_AIRNESS
        = "AGENTS.md has stale Airness instructions (run mvn airness:assets-sync)";
    private static final String MALFORMED_AIRNESS
        = "AGENTS.md has malformed, duplicate, or non-leading Airness instruction markers";
    private static final String FORBIDDEN_CLAUDE
        = "CLAUDE.md files are forbidden because Claude Code reads AGENTS.md (delete them)";

    private final Path root;
    private final AgentInstructions instructions;

    /**
     * Names the working tree that must carry {@code AGENTS.md} and no {@code CLAUDE.md}.
     *
     * @param root      the working tree root
     * @param canonical the exact Airness-owned section required at the start of {@code AGENTS.md}
     */
    public EntryFileCheck(Path root, String canonical) {
        this.root = root;
        this.instructions = new AgentInstructions(root, canonical);
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
            new Findings(MISSING_AIRNESS, this.missingAirness()),
            new Findings(MALFORMED_AIRNESS, this.malformedAirness()),
            new Findings(FORBIDDEN_CLAUDE, this.forbiddenClaude())
        );
    }

    private List<String> missingAirness() {
        return this.read(EntryFileRules.INSTRUCTIONS)
            .filter(content -> !this.instructions.malformed(content))
            .filter(content -> !this.instructions.current(content))
            .map(_ -> List.of(EntryFileRules.INSTRUCTIONS))
            .orElseGet(List::of);
    }

    private List<String> malformedAirness() {
        return this.read(EntryFileRules.INSTRUCTIONS)
            .filter(this.instructions::malformed)
            .map(_ -> List.of(EntryFileRules.INSTRUCTIONS))
            .orElseGet(List::of);
    }

    private List<String> missingInstructions() {
        return this.read(EntryFileRules.INSTRUCTIONS).isEmpty()
            ? List.of(EntryFileRules.INSTRUCTIONS)
            : List.of();
    }

    private List<String> emptyInstructions() {
        return this.read(EntryFileRules.INSTRUCTIONS)
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

    private Optional<String> read(String name) {
        return Repository.readText(this.root.resolve(name));
    }
}
