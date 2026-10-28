package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import eu.ciechanowiec.airness.governance.ReportAdvisory;
import java.nio.file.Path;
import java.util.List;

/**
 * The mitigation declarations of one declared Node project, checked against that project's current findings.
 *
 * @param directory the declared project's root, not a workspace member
 * @param entries   the complete declarations
 */
record NpmMitigations(Path directory, List<NpmMitigation> entries) {

    private static final String SETTING = "airness.vulnerabilityAllowlist";

    NpmMitigations {
        entries = List.copyOf(entries);
    }

    static NpmMitigations read(Path directory) {
        JsonNode declarations = ReportJson.read(directory.resolve("package.json"))
            .path("airness").path("vulnerabilityAllowlist");
        if (declarations.isMissingNode()) {
            return new NpmMitigations(directory, List.of());
        }
        if (!declarations.isArray()) {
            throw new IllegalStateException(directory + "/package.json: " + SETTING + " must be an array");
        }
        return new NpmMitigations(directory, declarations.valueStream().map(NpmMitigation::of).toList());
    }

    List<ReportAdvisory> apply(List<OsvAdvisory> findings) {
        List<String> unused = this.entries.stream()
            .filter(entry -> findings.stream().noneMatch(entry::matches)).map(NpmMitigation::description).toList();
        if (!unused.isEmpty()) {
            throw new IllegalStateException(
                this.directory + "/package.json: unused " + SETTING + " entries: " + unused
            );
        }
        return findings.stream().map(this::mitigate).sorted(ReportAdvisory.TRIAGE).toList();
    }

    private ReportAdvisory mitigate(OsvAdvisory finding) {
        List<NpmMitigation> matching = this.entries.stream().filter(entry -> entry.matches(finding)).toList();
        if (matching.size() > 1) {
            throw new IllegalStateException(
                this.directory + "/package.json: multiple " + SETTING + " entries match " + finding.advisory().key()
            );
        }
        return matching.isEmpty() ? finding.advisory() : matching.getFirst().apply(finding.advisory());
    }
}
