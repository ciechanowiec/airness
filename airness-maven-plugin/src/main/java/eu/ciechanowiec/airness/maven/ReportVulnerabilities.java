package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import eu.ciechanowiec.airness.governance.ReportAdvisory;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Reads Dependency-Check's evaluated findings, including the exceptions it actually applied.
 */
@UtilityClass
final class ReportVulnerabilities {

    private static final String ERRORS = "analysisExceptions";

    static List<ReportAdvisory> read(Path file, Instant started, String module) {
        JsonNode document = ReportJson.read(file);
        JsonNode project = document.path("projectInfo");
        ReportLicenses.current(ReportJson.required(project, "reportDate"), started);
        JsonNode scan = document.path("scanInfo");
        JsonNode dependencies = document.path("dependencies");
        if (!dependencies.isArray() || !scan.path(ERRORS).isEmpty()) {
            throw new IllegalStateException("Incomplete vulnerability evidence: " + file);
        }
        return dependencies.valueStream().flatMap(dependency -> advisories(dependency, module)).toList();
    }

    private static Stream<ReportAdvisory> advisories(JsonNode dependency, String module) {
        String artifact = dependency.path("packages").valueStream().map(entry -> entry.path("id").asText())
            .findFirst().orElseGet(() -> ReportJson.required(dependency, "fileName"));
        return Stream.concat(
            dependency.path("vulnerabilities").valueStream().map(
                entry -> advisory(entry, artifact, module, "Unresolved")
            ),
            dependency.path("suppressedVulnerabilities").valueStream()
                .map(entry -> advisory(entry, artifact, module, "Suppressed"))
        );
    }

    private static String severity(JsonNode entry) {
        JsonNode fourth = entry.path("cvssv4");
        JsonNode third = entry.path("cvssv3");
        JsonNode second = entry.path("cvssv2");
        return Stream.of(
            entry.path("severity"), fourth.path("baseSeverity"), third.path("baseSeverity"), second.path("severity")
        ).filter(JsonNode::isTextual).map(JsonNode::asText).filter(value -> !value.isBlank())
            .findFirst().orElse("Unknown");
    }

    private static ReportAdvisory advisory(JsonNode entry, String artifact, String module, String status) {
        return new ReportAdvisory(
            ReportJson.required(entry, "name"), severity(entry), status,
            Map.of(
                "Artifact", artifact, "Module", module, "Description", entry.path("description").asText(),
                "Exception", entry.path("notes").asText()
            )
        );
    }
}
