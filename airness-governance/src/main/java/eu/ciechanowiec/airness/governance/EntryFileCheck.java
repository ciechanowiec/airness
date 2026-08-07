package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The repository carries the managed Airness section and project instructions in {@code AGENTS.md},
 * exposing both to Claude through a fixed {@code CLAUDE.md} entry file.
 *
 * <p>The names and the reference are constants rather than project parameters. A configurable path
 * could make the check pass over a different file, which would leave the tools that read these fixed
 * names outside the contract.
 */
public final class EntryFileCheck {

    private static final String MISSING_INSTRUCTIONS = "The mandatory AGENTS.md file is missing";
    private static final String EMPTY_INSTRUCTIONS = "AGENTS.md contains no instructions";
    private static final String MISSING_AIRNESS
        = "AGENTS.md is missing or has stale Airness instructions (run mvn airness:assets-sync)";
    private static final String MALFORMED_AIRNESS
        = "AGENTS.md has malformed, duplicate, or non-leading Airness instruction markers";
    private static final String MISSING_CLAUDE = "The mandatory CLAUDE.md file is missing";
    private static final String WRONG_CLAUDE = "CLAUDE.md must contain exactly @AGENTS.md";

    private final Path root;
    private final AgentInstructions instructions;

    /**
     * Names the working tree that must carry both fixed files.
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
            new Findings(MISSING_CLAUDE, this.missingClaude()),
            new Findings(WRONG_CLAUDE, this.wrongClaude())
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

    private List<String> missingClaude() {
        return this.read(EntryFileRules.CLAUDE).isEmpty()
            ? List.of(EntryFileRules.CLAUDE)
            : List.of();
    }

    private List<String> wrongClaude() {
        return this.read(EntryFileRules.CLAUDE)
            .filter(content -> !EntryFileRules.isClaudeEntry(content))
            .map(_ -> List.of(EntryFileRules.CLAUDE))
            .orElseGet(List::of);
    }

    private Optional<String> read(String name) {
        return Repository.readText(this.root.resolve(name));
    }
}
