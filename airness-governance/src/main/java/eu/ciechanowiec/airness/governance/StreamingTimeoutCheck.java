package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.List;

/**
 * Requires a complete current assessment and refuses every unverifiable streaming configuration.
 */
public final class StreamingTimeoutCheck {

    private final StreamingTimeoutInputs inputs;
    private final boolean prepared;
    private final List<StreamingTimeoutEvidence> results;

    /**
     * Reads evidence only after the production manifest has been checked.
     *
     * @param inputs   the expected production inputs
     * @param manifest the prepared manifest
     * @param evidence the ready-context evidence
     */
    public StreamingTimeoutCheck(StreamingTimeoutInputs inputs, Path manifest, Path evidence) {
        this.inputs = inputs;
        this.prepared = inputs.matches(manifest);
        this.results = this.prepared ? StreamingTimeoutEvidence.read(evidence, inputs) : List.of();
    }

    /**
     * Counts completed context assessments.
     *
     * @return the number of assessed contexts
     */
    public long assessed() {
        return this.results.stream().filter(result -> "assessed".equals(result.status())).count();
    }

    /**
     * Supplies the missing-evidence and configuration verdicts.
     *
     * @return failures without treating unsupported configurations as successes
     */
    public List<Findings> findings() {
        return List.of(
            new Findings("Streaming timeouts without a current runtime assessment", this.unproved()),
            new Findings("Streaming endpoints without a production timeout policy", this.failures("missing")),
            new Findings("Streaming endpoints inheriting the servlet container timeout", this.failures("inherited")),
            new Findings("Streaming timeout configuration cannot be verified", this.failures("unsupported")),
            new Findings("Streaming timeout assessment failed", this.failures("error"))
        );
    }

    private List<String> unproved() {
        return this.inputs.applications().stream().filter(application -> !this.complete(application))
            .map(application -> application + ": run the lifecycle through tests with current streaming timeout inputs")
            .toList();
    }

    private boolean complete(String application) {
        return this.prepared && this.results.stream().anyMatch(
            result -> application.equals(result.application())
                && "assessed".equals(result.status())
        );
    }

    private List<String> failures(String status) {
        return this.results.stream().filter(result -> status.equals(result.status()))
            .map(result -> result.application() + " (" + result.context() + "): " + result.detail())
            .distinct().sorted().toList();
    }
}
