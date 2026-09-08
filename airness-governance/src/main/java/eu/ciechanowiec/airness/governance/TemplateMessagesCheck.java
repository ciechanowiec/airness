package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Enforces literal base-message lookups proved by this build's ready application contexts.
 */
public final class TemplateMessagesCheck {

    private final TemplateMessageInputs inputs;
    private final boolean prepared;
    private final List<TemplateMessageEvidence> results;

    /**
     * Reads only results tied to this invocation and this source inventory.
     *
     * @param inputs   the current source inventory
     * @param manifest the pre-test manifest
     * @param evidence the existing Spring context evidence file
     */
    public TemplateMessagesCheck(TemplateMessageInputs inputs, Path manifest, Path evidence) {
        this.inputs = inputs;
        this.prepared = inputs.matches(manifest);
        this.results = this.prepared ? TemplateMessageEvidence.read(evidence, inputs) : List.of();
    }

    /**
     * Counts checked lookups across the supported ready contexts.
     *
     * @return the lookup count
     */
    public int checked() {
        return this.results.stream().mapToInt(TemplateMessageEvidence::checked).sum();
    }

    /**
     * Reports configurations or resources that this first version cannot assess.
     *
     * @return the explicitly unassessed scopes
     */
    public List<String> unassessed() {
        return this.results.stream().filter(result -> "unassessed".equals(result.status()))
            .map(result -> result.application() + " (" + result.context() + "): " + result.detail()).distinct().sorted()
            .toList();
    }

    /**
     * Refuses absent evidence, undefined names and lookup failures independently.
     *
     * @return the rule verdicts
     */
    public List<Findings> findings() {
        return List.of(
            new Findings("Template message references without a current runtime assessment", this.unproved()),
            new Findings("Literal template message keys missing from base messages", this.missing()),
            new Findings("Template message assessments that failed", this.errors())
        );
    }

    private List<String> unproved() {
        if (this.inputs.references().isEmpty()) {
            return List.of();
        }
        return this.inputs.applications().stream().filter(application -> !this.complete(application))
            .map(application -> application + ": run the lifecycle through tests with current template message inputs")
            .toList();
    }

    private boolean complete(String application) {
        return this.prepared && this.results.stream().anyMatch(
            result -> result.application().equals(application)
                && result.complete()
        );
    }

    private List<String> missing() {
        return this.results.stream().filter(result -> "missing".equals(result.status()))
            .map(this::missing).distinct().sorted().toList();
    }

    private String missing(TemplateMessageEvidence result) {
        TemplateMessageReference reference = this.reference(result.detail()).orElseThrow(
            () -> new IllegalArgumentException("Message evidence names a reference outside this manifest")
        );
        return ("%s: %s is missing from base messages for %s (%s). "
            + "Define the key in the selected base messages or correct the template reference")
            .formatted(reference.location(), reference.key(), result.application(), result.context());
    }

    private Optional<TemplateMessageReference> reference(String encoded) {
        return this.inputs.references().stream().filter(reference -> reference.encoded().equals(encoded)).findFirst();
    }

    private List<String> errors() {
        return this.results.stream().filter(result -> "error".equals(result.status()))
            .map(result -> result.application() + " (" + result.context() + "): " + result.detail()).distinct().sorted()
            .toList();
    }
}
