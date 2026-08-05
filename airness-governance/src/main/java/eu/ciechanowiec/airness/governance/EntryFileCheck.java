package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The repository carries project instructions in {@code AGENTS.md} and exposes them to Claude through
 * a fixed {@code CLAUDE.md} entry file.
 *
 * <p>The names and the reference are constants rather than project parameters. A configurable path
 * could make the check pass over a different file, which would leave the tools that read these fixed
 * names outside the contract.
 */
public final class EntryFileCheck {

    private static final String MISSING_INSTRUCTIONS = "The mandatory AGENTS.md file is missing";
    private static final String EMPTY_INSTRUCTIONS = "AGENTS.md contains no instructions";
    private static final String MISSING_CLAUDE = "The mandatory CLAUDE.md file is missing";
    private static final String WRONG_CLAUDE = "CLAUDE.md must contain exactly @AGENTS.md";

    private final Path root;

    /**
     * Names the working tree that must carry both fixed files.
     *
     * @param root the working tree root
     */
    public EntryFileCheck(Path root) {
        this.root = root;
    }

    /**
     * The four ways this arrangement breaks, each reported separately.
     *
     * @return one verdict per rule
     */
    public List<Findings> findings() {
        return List.of(
            new Findings(MISSING_INSTRUCTIONS, this.missingInstructions()),
            new Findings(EMPTY_INSTRUCTIONS, this.emptyInstructions()),
            new Findings(MISSING_CLAUDE, this.missingClaude()),
            new Findings(WRONG_CLAUDE, this.wrongClaude())
        );
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
